"""
A script to download native artifacts
"""
__author__ = 'shuangshun'

import os
import io
import sys
import time
import shutil
import zipfile
import hashlib
import requests

from requests.exceptions import HTTPError, Timeout, ConnectionError


class GitHubAPI:
    def __init__(self):
        self.base_url = 'https://api.github.com/repos/chenjunfu2/Litematic_V7_To_V6_DynamicLibrary/actions'
        self.branch = 'master'
        self.token = os.environ.get('NATIVE_TOKEN')
        self.session = requests.Session()
        self.session.headers.update({
            'Accept': 'application/vnd.github.v3+json'
        })
        if self.token:
            self.session.headers['Authorization'] = f'Bearer {self.token}'

    def _request(self, url, method='GET', data=None):
        kwargs = {
            'method': method,
            'url': self.base_url + url,
            'timeout': 30,
        }
        if data is not None:
            kwargs['json'] = data

        try:
            response = self.session.request(**kwargs)

            if response.status_code == 204:
                return None

            response.raise_for_status()
            return response.json()
        except HTTPError as e:
            error_body = e.response.text if e.response else ''
            print(f"HTTP Error {e.response.status_code}: {error_body}", file=sys.stderr)
            raise
        except (Timeout, ConnectionError) as e:
            print(f"Request failed: {e}", file=sys.stderr)
            raise

    def trigger_workflow(self, workflow_id, inputs=None):
        url = f"/workflows/{workflow_id}/dispatches"
        data = {
            'ref': self.branch,
            'inputs': inputs or {}
        }
        print(f"Triggering workflow {workflow_id} (ref: {self.branch})")
        self._request(url, method='POST', data=data)
        print("✓ Workflow triggered successfully")

    def get_latest_workflow_run(self, workflow_id, status='success'):
        url = f"/workflows/{workflow_id}/runs"
        params = []
        if status:
            params.append(f"status={status}")
        params.append(f"branch={self.branch}")
        params.append("per_page=1")

        query_string = "&".join(params)
        url = f"{url}?{query_string}"

        result = self._request(url)
        if result['workflow_runs']:
            return result['workflow_runs'][0]
        return None

    def check_for_workflow_completion(self, run_id, max_interval=60):
        start_time = time.time()
        current_interval = 10
        print(f"Waiting for workflow run {run_id} to complete...")

        while time.time() - start_time < 600:
            try:
                url = f"/runs/{run_id}"
                result = self._request(url)
            except Exception as e:
                print(f"Request failed: {e}, retrying in {current_interval}s", file=sys.stderr)
                time.sleep(current_interval)
                current_interval = min(current_interval * 1.5, max_interval)
                continue

            status = result['status']
            conclusion = result.get('conclusion', 'pending')

            if status == 'completed':
                if conclusion == 'success':
                    print("✓ Workflow completed successfully")
                    return True
                else:
                    print(f"✗ Workflow failed with conclusion: {conclusion}", file=sys.stderr)
                    return False

            time.sleep(current_interval)
            current_interval = min(current_interval * 1.2, max_interval)

        print(f"✗ Timeout waiting for workflow completion", file=sys.stderr)
        return False

    def list_artifacts(self, run_id):
        url = f"/runs/{run_id}/artifacts"
        result = self._request(url)
        return result.get('artifacts', [])

    def download_artifact(self, artifact_id):
        url = f"{self.base_url}/artifacts/{artifact_id}/zip"

        response = self.session.get(url, allow_redirects=False, stream=True, timeout=60)
        if response.status_code == 200:
            return response.content
        elif response.status_code not in (301, 302, 303, 307, 308):
            response.raise_for_status()

        redirect_url = response.headers.get('Location')
        if not redirect_url:
            raise RuntimeError("No Location header in redirect")

        # Without Authorization header
        download_headers = {
            k: v for k, v in self.session.headers.items()
            if k.lower() != 'authorization'
        }
        try:
            final_response = requests.get(
                redirect_url,
                headers=download_headers,
                stream=True,
                timeout=60
            )
            final_response.raise_for_status()
            return final_response.content
        except HTTPError as e:
            print(f"Failed to download artifact: {e.response.status_code}", file=sys.stderr)
            raise
        except (Timeout, ConnectionError) as e:
            print(f"Download request failed: {e}", file=sys.stderr)
            raise


def print_step(step, newline=True):
    if newline:
        print()
    print("=" * 60)
    print(step)
    print("=" * 60)


def extract_zip(zip_data, extract_dir):
    os.makedirs(extract_dir, exist_ok=True)

    with zipfile.ZipFile(io.BytesIO(zip_data)) as zf:
        zf.extractall(extract_dir)


def main():
    workflow = 'c-cpp.yml'
    output_dir = 'build/generated/natives'
    trigger_new = os.environ.get('TRIGGER_NEW_WORKFLOW', 'false').lower() == 'true'

    github = GitHubAPI()

    try:
        if trigger_new:
            print_step("Triggering new workflow run", False)
            github.trigger_workflow(workflow)

            print("Waiting for workflow to start...")
            time.sleep(5)

            run = github.get_latest_workflow_run(workflow, status='')
            if not run:
                print("✗ Could not find triggered workflow run", file=sys.stderr)
                sys.exit(1)

            run_id = run['id']

        else:
            print_step("Using existing workflow run", False)
            run = github.get_latest_workflow_run(workflow, status='success')
            if not run:
                print(f"✗ No workflow run found for {workflow}", file=sys.stderr)
                sys.exit(1)

            run_id = run['id']

        print(f"Using workflow run: {run_id}")

        print_step("Check for workflow completion")
        success = github.check_for_workflow_completion(run_id)

        if not success:
            print("✗ Workflow did not complete successfully", file=sys.stderr)
            sys.exit(1)

        print_step("Downloading and extract artifacts")

        artifacts = github.list_artifacts(run_id)
        print(f"Found {len(artifacts)} artifacts:")
        for art in artifacts:
            print(f"  - {art['name']} ({art['size_in_bytes']} bytes)")

        if not artifacts:
            print(f"✗ No artifacts found", file=sys.stderr)
            sys.exit(1)

        artifacts_digest = {}
        for art in artifacts:
            artifacts_digest[art['name']] = art['digest'].replace('sha256:', '')

        extracted_count = 0
        verification_failed = []

        for target_artifact in artifacts:
            artifact_name = target_artifact['name']
            expected_digest = artifacts_digest[artifact_name]

            print(f"\nDownloading artifact: {artifact_name}")
            zip_data = github.download_artifact(target_artifact['id'])

            print(f"Verifying artifact...")
            actual_digest = hashlib.sha256(zip_data).hexdigest()

            if actual_digest != expected_digest:
                verification_failed.append(artifact_name)
                print(f"  ✗ {artifact_name} - SHA256 mismatch!")
                print(f"    Expected: {expected_digest}")
                print(f"    Got:      {actual_digest}")
                continue

            print(f"  ✓ Artifact verified successfully")

            temp_extract = os.path.join(output_dir, '.temp_extract')
            extract_zip(zip_data, temp_extract)

            items = os.listdir(temp_extract)
            subdirs = [d for d in items if os.path.isdir(os.path.join(temp_extract, d))]
            if len(subdirs) == 1:
                platform_dir = subdirs[0]
                target_path = os.path.join(output_dir, platform_dir)
                if os.path.exists(target_path):
                    shutil.rmtree(target_path)
                shutil.move(os.path.join(temp_extract, platform_dir), target_path)
                shutil.rmtree(temp_extract)
                print(f"Extracted to: {target_path}")
            else:
                shutil.rmtree(temp_extract)
                print(f"Error: unexpected zip structure (subdirs: {subdirs})")
                sys.exit(1)

            extracted_count += 1

        if verification_failed:
            print(f"\n✗ Verification failed for {len(verification_failed)} artifact(s):", file=sys.stderr)
            for name in verification_failed:
                print(f"    - {name}", file=sys.stderr)
            sys.exit(1)

        if extracted_count == 0:
            print("✗ No artifacts were successfully extracted", file=sys.stderr)
            sys.exit(1)

        print_step(f"SUCCESS: Downloaded {extracted_count} platform artifacts")
    except Exception as e:
        print(f"\n✗ Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()