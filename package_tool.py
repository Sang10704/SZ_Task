# -*- coding: utf-8 -*-
"""
SEAZEN TASK - CONG CU DONG GOI APK TU DONG
Cho phep nguoi dung nhap phien ban de dong goi ma khong can mo Android Studio.
"""

import os
import re
import sys
import shutil
import subprocess
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = SCRIPT_DIR
APP_BUILD_GRADLE = os.path.join(PROJECT_DIR, "app", "build.gradle.kts")
TASK_REPO_FILE = os.path.join(PROJECT_DIR, "app", "src", "main", "java", "com", "seazen", "task", "data", "TaskRepository.kt")
RELEASE_APK_SOURCE = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "release", "app-release.apk")
ROOT_WORKSPACE = os.path.abspath(os.path.join(PROJECT_DIR, ".."))
DEST_APK_ROOT = os.path.join(ROOT_WORKSPACE, "SZ_Task.apk")
DEST_APK_LOCAL = os.path.join(PROJECT_DIR, "SZ_Task.apk")
JAVA_HOME_PATH = r"C:\Program Files\Android\Android Studio\jbr"

def print_header():
    print("=" * 60)
    print("         SEAZEN TASK - CONG CU DONG GOI APK TU DONG")
    print("=" * 60)

def get_current_gradle_version():
    if not os.path.exists(APP_BUILD_GRADLE):
        return "0.9.1", 1
    with open(APP_BUILD_GRADLE, "r", encoding="utf-8") as f:
        content = f.read()
    vname_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    vcode_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    vname = vname_match.group(1) if vname_match else "0.9.1"
    vcode = int(vcode_match.group(1)) if vcode_match else 1
    return vname, vcode

def update_gradle_version(new_version, new_code):
    with open(APP_BUILD_GRADLE, "r", encoding="utf-8") as f:
        content = f.read()
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_version}"', content)
    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', content)
    with open(APP_BUILD_GRADLE, "w", encoding="utf-8", newline="\r\n") as f:
        f.write(content)

def update_repo_fallback(new_version):
    if not os.path.exists(TASK_REPO_FILE):
        return
    with open(TASK_REPO_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    # Replace fallback inside getCurrentAppVersion()
    pattern = r'(fun getCurrentAppVersion\(\): String \{[\s\S]*?pInfo\.versionName \?:\s*")[^"]+("[\s\S]*?\?\:\s*")[^"]+("\s*\}\s*\})'
    content = re.sub(pattern, f'\\g<1>{new_version}\\g<2>{new_version}\\g<3>', content)
    with open(TASK_REPO_FILE, "w", encoding="utf-8", newline="\r\n") as f:
        f.write(content)

def update_sharepoint_version(new_version):
    portal_app_dir = os.path.join(ROOT_WORKSPACE, "SEAZEN_HR_PORTAL_APP")
    if not os.path.exists(portal_app_dir):
        print("[SharePoint] Khong tim thay thu muc SEAZEN_HR_PORTAL_APP, bo qua cap nhat SharePoint.")
        return False
    try:
        if portal_app_dir not in sys.path:
            sys.path.insert(0, portal_app_dir)
        import requests
        from app.core.graph_client import SharePointCoreClient
        from app.core.config import LIST_APP_VERSION

        print("[SharePoint] Dang xac thuc Bearer Token qua App Registration...")
        client = SharePointCoreClient()
        token = client.acquire_sharepoint_token()
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json;odata=verbose",
            "Content-Type": "application/json;odata=verbose"
        }
        digest_res = requests.post(
            "https://seazenvn.sharepoint.com/sites/ONESEAZEN/_api/contextinfo",
            headers={"Authorization": f"Bearer {token}", "Accept": "application/json;odata=verbose"}
        )
        digest = digest_res.json()["d"]["GetContextWebInformation"]["FormDigestValue"]
        headers["X-RequestDigest"] = digest
        headers["X-HTTP-Method"] = "MERGE"
        headers["IF-MATCH"] = "*"

        url = f"https://seazenvn.sharepoint.com/sites/ONESEAZEN/_api/web/lists(guid'{LIST_APP_VERSION}')/items(11)"
        payload = {
            "__metadata": {"type": "SP.Data.POWERAPP_x0020_LATEST_x0020_VERSIONListItem"},
            "LatestVersion": new_version
        }
        res = requests.post(url, headers=headers, json=payload)
        if res.status_code in [200, 204]:
            print(f"[SharePoint] Thanh cong! Da cap nhat muc 11 (SZ_TASK) len v{new_version} tren Cloud.")
            return True
        else:
            print(f"[SharePoint] Loi cap nhat: HTTP {res.status_code} - {res.text}")
            return False
    except Exception as e:
        print(f"[SharePoint] Loi: {e}")
        return False

def build_apk():
    print("-" * 60)
    print("Dang tien hanh bien dich APK Release qua Gradle...")
    print(f"Thu muc du an: {PROJECT_DIR}")
    print(f"JAVA_HOME: {JAVA_HOME_PATH}")
    print("-" * 60)

    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME_PATH
    gradlew_cmd = os.path.join(PROJECT_DIR, "gradlew.bat")

    start_time = time.time()
    result = subprocess.run([gradlew_cmd, "assembleRelease"], cwd=PROJECT_DIR, env=env)
    duration = time.time() - start_time

    if result.returncode != 0:
        print(f"\n[THAT BAI] Qua trinh bien dich APK gap loi (ma loi: {result.returncode}).")
        return False

    if not os.path.exists(RELEASE_APK_SOURCE):
        print(f"\n[THAT BAI] Khong tim thay file APK dau ra tai: {RELEASE_APK_SOURCE}")
        return False

    file_size_mb = os.path.getsize(RELEASE_APK_SOURCE) / (1024 * 1024)

    # Sao chep den D:\SeaZen APP\SZ_Task.apk va SeaZenTaskApp\SZ_Task.apk
    shutil.copy2(RELEASE_APK_SOURCE, DEST_APK_ROOT)
    shutil.copy2(RELEASE_APK_SOURCE, DEST_APK_LOCAL)

    print("\n" + "=" * 60)
    print("                DONG GOI THANH CONG!")
    print("=" * 60)
    print(f"Thoi gian bien dich: {duration:.1f} giay")
    print(f"Dung luong APK:      {file_size_mb:.2f} MB")
    print(f"File xuat ban:       {DEST_APK_ROOT}")
    print(f"Tuan thu quy chuan:  Ten file luon la 'SZ_Task.apk' (khong luu version cu)")
    print("=" * 60)
    return True

def main():
    print_header()
    curr_ver, curr_code = get_current_gradle_version()
    print(f"Phien ban hien tai trong du an: v{curr_ver} (build code: {curr_code})")
    print()

    # Nhap phien ban
    user_input = input(f"Nhap phien ban moi can dong goi [Nhan Enter de dung {curr_ver}]: ").strip()
    if user_input:
        target_version = user_input.lstrip("v").strip()
        target_code = curr_code + 1
    else:
        target_version = curr_ver
        target_code = curr_code

    print(f"\n-> Phien ban se dong goi: v{target_version} (versionCode: {target_code})")

    # Hoi cap nhat SharePoint
    sp_choice = input("\nDong bo phien ban len SharePoint 'POWERAPP LATEST VERSION' de app tu dong bao OTA? (Y/n): ").strip().lower()
    should_update_sp = sp_choice != "n"

    # Cap nhat ma nguon
    print("\nDang cap nhat thong so phien ban vao ma nguon...")
    update_gradle_version(target_version, target_code)
    update_repo_fallback(target_version)
    print("[Ma nguon] Da cap nhat build.gradle.kts va TaskRepository.kt.")

    # Dong bo SharePoint neu nguoi dung chon
    if should_update_sp:
        update_sharepoint_version(target_version)

    # Bien dich APK
    success = build_apk()

    if success:
        open_folder = input("\nMo thu muc chua file SZ_Task.apk tren Windows Explorer? (Y/n): ").strip().lower()
        if open_folder != "n":
            subprocess.run(["explorer", f"/select,{DEST_APK_ROOT}"])

    input("\nNhan Enter de thoat...")

if __name__ == "__main__":
    main()
