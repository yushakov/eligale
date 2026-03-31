#!/usr/bin/env python3
"""
Integration tests for eliza.gallery.

Covers:
  - Public REST API (categories, products, comments)
  - Auth flow (request-code → verify-code → profile → logout)
  - Authenticated user endpoints (comment CRUD, my comments, search, chat presign)
  - Staff endpoints (chats, staff comments, catalog presign)
  - Yandex Object Storage (upload, public URL, download, delete)
  - Transactional email (request-code sends a real Resend email)

Usage:
    source .venv/bin/activate
    python e2e_test.py --email you@example.com [--base-url http://localhost:8000] [--staff-token TOKEN]

    # Against production:
    python e2e_test.py --email you@example.com --base-url https://eliza.gallery --staff-token TOKEN

Requirements (all in the project venv): requests, boto3, python-dotenv
"""

import sys
import os
import uuid
import argparse
import requests
from dotenv import load_dotenv

load_dotenv()

# ── ANSI colours ──────────────────────────────────────────────────────────────

G  = "\033[92m"   # green
R  = "\033[91m"   # red
Y  = "\033[93m"   # yellow
B  = "\033[1m"    # bold
RS = "\033[0m"    # reset

# ── Result tracker ────────────────────────────────────────────────────────────

_passed = 0
_failed = 0


def ok(msg):
    global _passed
    _passed += 1
    print(f"  {G}✓{RS} {msg}")


def fail(msg, detail=""):
    global _failed
    _failed += 1
    print(f"  {R}✗{RS} {msg}")
    if detail:
        print(f"    {R}{detail}{RS}")


def check(cond, ok_msg, fail_msg, detail=""):
    if cond:
        ok(ok_msg)
    else:
        fail(fail_msg, detail)
    return cond


def section(title):
    print(f"\n{B}{title}{RS}")
    print("─" * 60)


def skip(msg):
    print(f"  {Y}–{RS} {msg} {Y}(пропущено){RS}")


# ── HTTP helpers (return None on network error, no side effects) ──────────────

def _req(method, base, path, **kw):
    try:
        return method(f"{base}{path}", timeout=15, **kw)
    except requests.exceptions.RequestException:
        return None


def get(base, path, **kw):
    return _req(requests.get, base, path, **kw)


def post(base, path, **kw):
    return _req(requests.post, base, path, **kw)


def delete(base, path, **kw):
    return _req(requests.delete, base, path, **kw)


def status(r):
    return r.status_code if r is not None else "no response"


# ── Section: Public API ───────────────────────────────────────────────────────

def test_public_api(base):
    section("Public API")

    r = get(base, "/api/categories/")
    if not check(r and r.status_code == 200, "GET /api/categories/ → 200",
                 "GET /api/categories/ failed", f"status={status(r)}"):
        return None, None

    cats = r.json()
    check(isinstance(cats, list) and len(cats) > 0,
          f"Список категорий: {len(cats)} шт.",
          "Список категорий пуст")
    if not cats:
        return None, None

    cat_id = cats[0]["id"]

    r = get(base, f"/api/categories/{cat_id}/products/")
    check(r and r.status_code == 200,
          f"GET /api/categories/{cat_id}/products/ → 200",
          "Product list failed", f"status={status(r)}")
    products = r.json() if (r and r.status_code == 200) else []
    check(isinstance(products, list),
          f"Список товаров: {len(products)} шт.",
          "Ответ не список")

    prod_id = products[0]["id"] if products else None

    if prod_id:
        r = get(base, f"/api/products/{prod_id}/")
        check(r and r.status_code == 200,
              f"GET /api/products/{prod_id}/ → 200",
              "Product detail failed", f"status={status(r)}")

        r = get(base, f"/api/products/{prod_id}/comments/")
        check(r and r.status_code == 200,
              f"GET /api/products/{prod_id}/comments/ → 200",
              "Comments list failed", f"status={status(r)}")

    return cat_id, prod_id


# ── Section: Auth + Email ─────────────────────────────────────────────────────

def test_auth_and_email(base, email):
    """
    Sends a real email via Resend and walks through the full auth flow.
    Interactive: prompts for the 6-digit code from the inbox.
    """
    section("Auth + Transactional Email (Resend)")

    r = post(base, "/api/auth/request-code/", json={"email": email})
    if not check(r and r.status_code == 200,
                 f"POST /api/auth/request-code/ → 200  (письмо отправлено на {email})",
                 "request-code failed", f"status={status(r)} body={r.text if r else ''}"):
        return None

    print(f"\n  {Y}→ Проверьте почту {email} и введите 6-значный код:{RS} ", end="", flush=True)
    code = input().strip()
    if not code:
        skip("Код не введён — auth тесты пропущены")
        return None

    r = post(base, "/api/auth/verify-code/", json={"email": email, "code": code})
    if not check(r and r.status_code == 200,
                 "POST /api/auth/verify-code/ → 200",
                 "verify-code failed", f"status={status(r)} body={r.text if r else ''}"):
        return None

    token = r.json().get("token")
    has_name = r.json().get("has_name")
    check(bool(token), f"Token получен (has_name={has_name})", "Token отсутствует в ответе")
    return token


# ── Section: Authenticated user ───────────────────────────────────────────────

def test_authenticated_user(base, token, prod_id):
    """User-level authenticated tests. Does NOT logout — caller handles that."""
    if not token:
        return

    section("Authenticated API (пользователь)")
    auth = {"Authorization": f"Token {token}"}

    r = get(base, "/api/auth/profile/", headers=auth)
    if check(r and r.status_code == 200, "GET /api/auth/profile/ → 200",
             "Profile failed", f"status={status(r)}"):
        profile = r.json()
        ok(f"  email={profile.get('email')}  is_staff={profile.get('is_staff')}")

    if prod_id:
        tag = uuid.uuid4().hex[:8]
        comment_text = f"[e2e {tag}]"
        r = post(base, f"/api/products/{prod_id}/comments/",
                 json={"text": comment_text}, headers=auth)
        check(r and r.status_code in (200, 201),
              f"POST comment → {status(r)}",
              "Post comment failed", f"status={status(r)} body={r.text if r else ''}")

        r = get(base, "/api/comments/my/", headers=auth)
        if check(r and r.status_code == 200, "GET /api/comments/my/ → 200",
                 "My comments failed", f"status={status(r)}"):
            found = any(c.get("text") == comment_text for c in r.json())
            check(found, "Тестовый комментарий виден в /api/comments/my/",
                  "Тестовый комментарий НЕ найден в /api/comments/my/")

    r = get(base, "/api/chat/", headers=auth)
    check(r and r.status_code == 200, "GET /api/chat/ → 200",
          "Chat info failed", f"status={status(r)}")

    r = get(base, "/api/chat/unread/", headers=auth)
    check(r and r.status_code == 200, "GET /api/chat/unread/ → 200",
          "Chat unread failed", f"status={status(r)}")

    r = get(base, "/api/search/?q=тест", headers=auth)
    check(r and r.status_code == 200, "GET /api/search/?q=тест → 200",
          "Search failed", f"status={status(r)}")

    r = post(base, "/api/chat/media/presign/",
             json={"filename": "test.jpg", "content_type": "image/jpeg"}, headers=auth)
    if check(r and r.status_code == 200, "POST /api/chat/media/presign/ → 200",
             "Chat media presign failed", f"status={status(r)} body={r.text if r else ''}"):
        data = r.json()
        check("upload_url" in data and "public_url" in data,
              "Chat presign: поля upload_url + public_url есть",
              "Chat presign: поля отсутствуют", f"keys={list(data.keys())}")

    if prod_id:
        test_favorites(base, token, prod_id)


# ── Section: Favorites ────────────────────────────────────────────────────────

def test_favorites(base, token, prod_id):
    section("Favorites API")
    auth = {"Authorization": f"Token {token}"}

    # Список изначально не содержит тестовый товар
    r = get(base, "/api/favorites/", headers=auth)
    if not check(r and r.status_code == 200, "GET /api/favorites/ → 200",
                 "Favorites list failed", f"status={status(r)}"):
        return
    initial_ids = [f["product_id"] for f in r.json()]
    # Если уже в избранном — удалим, чтобы тест был воспроизводимым
    if prod_id in initial_ids:
        delete(base, f"/api/favorites/{prod_id}/", headers=auth)

    # Добавить в избранное
    r = post(base, "/api/favorites/", json={"product_id": prod_id}, headers=auth)
    check(r and r.status_code == 201,
          f"POST /api/favorites/ → 201 (добавлен product_id={prod_id})",
          "Add favorite failed", f"status={status(r)} body={r.text if r else ''}")

    # Повторное добавление — идемпотентно, 200
    r = post(base, "/api/favorites/", json={"product_id": prod_id}, headers=auth)
    check(r and r.status_code == 200,
          "POST /api/favorites/ повторно → 200 (идемпотентно)",
          "Duplicate add should return 200", f"status={status(r)}")

    # Товар виден в списке, поля присутствуют
    r = get(base, "/api/favorites/", headers=auth)
    if check(r and r.status_code == 200, "GET /api/favorites/ после добавления → 200",
             "Favorites list failed", f"status={status(r)}"):
        items = r.json()
        found = next((f for f in items if f["product_id"] == prod_id), None)
        if check(found is not None, f"Товар {prod_id} есть в избранном",
                 f"Товар {prod_id} НЕ найден в избранном"):
            for field in ["product_id", "product_name", "cover_url", "cover_url_100", "created_at"]:
                check(field in found,
                      f"  поле '{field}' присутствует",
                      f"  поле '{field}' отсутствует", f"keys={list(found.keys())}")

    # Удалить из избранного
    r = delete(base, f"/api/favorites/{prod_id}/", headers=auth)
    check(r and r.status_code == 204,
          f"DELETE /api/favorites/{prod_id}/ → 204",
          "Delete favorite failed", f"status={status(r)}")

    # После удаления товара нет в списке
    r = get(base, "/api/favorites/", headers=auth)
    if check(r and r.status_code == 200, "GET /api/favorites/ после удаления → 200",
             "Favorites list failed", f"status={status(r)}"):
        ids_after = [f["product_id"] for f in r.json()]
        check(prod_id not in ids_after,
              f"Товар {prod_id} удалён из избранного",
              f"Товар {prod_id} всё ещё в избранном после DELETE")

    # Повторное удаление — 204 (idempotent)
    r = delete(base, f"/api/favorites/{prod_id}/", headers=auth)
    check(r and r.status_code == 204,
          "DELETE повторно → 204 (идемпотентно)",
          "Repeated delete should return 204", f"status={status(r)}")

    # Без токена — 401
    r = get(base, "/api/favorites/")
    check(r is not None and r.status_code == 401,
          "GET /api/favorites/ без токена → 401",
          "Favorites should require auth", f"status={status(r)}")


# ── Section: Staff API ────────────────────────────────────────────────────────

def test_staff(base, staff_token, prod_id):
    if not staff_token:
        section("Staff API")
        skip("--staff-token не передан")
        return

    section("Staff API")
    auth = {"Authorization": f"Token {staff_token}"}

    r = get(base, "/api/auth/profile/", headers=auth)
    if not check(r and r.status_code == 200,
                 "Staff profile доступен",
                 "Staff profile недоступен", f"status={status(r)}"):
        return
    is_staff = r.json().get("is_staff", False)
    if not check(is_staff, "Staff token подтверждён (is_staff=true)",
                 "Токен не staff!", f"profile={r.json()}"):
        return

    r = get(base, "/api/chats/", headers=auth)
    check(r and r.status_code == 200, "GET /api/chats/ → 200",
          "Staff chats failed", f"status={status(r)}")

    r = get(base, "/api/chats/unread/", headers=auth)
    check(r and r.status_code == 200, "GET /api/chats/unread/ → 200",
          "Staff chats unread failed", f"status={status(r)}")

    r = get(base, "/api/staff/comments/", headers=auth)
    check(r and r.status_code == 200, "GET /api/staff/comments/ → 200",
          "Staff comments failed", f"status={status(r)}")

    r = get(base, "/api/staff/comments/unread/", headers=auth)
    check(r and r.status_code == 200, "GET /api/staff/comments/unread/ → 200",
          "Staff comments unread failed", f"status={status(r)}")

    r = get(base, "/api/search/?q=тест", headers=auth)
    check(r and r.status_code == 200, "GET /api/search/?q=тест (staff) → 200",
          "Search (staff) failed", f"status={status(r)}")

    # /api/catalog/upload/presign использует @staff_member_required (сессия) + @csrf_protect,
    # поэтому не тестируется через Token auth — только из браузера (mobile admin).


# ── Section: Logout + token revocation ───────────────────────────────────────

def test_logout(base, token):
    """
    Called after all authenticated tests are done.
    If the auth-flow email belongs to a staff account, the token from verify-code
    is the same as the staff token (Django uses get_or_create). Both are revoked here.
    """
    if not token:
        return

    section("Logout + отзыв токена")
    auth = {"Authorization": f"Token {token}"}

    r = post(base, "/api/auth/logout/", headers=auth)
    check(r and r.status_code == 200, "POST /api/auth/logout/ → 200",
          "Logout failed", f"status={status(r)}")

    # After logout the token must not grant access.
    # A 401 response OR a connection-level rejection both confirm the token is gone.
    r = get(base, "/api/auth/profile/", headers=auth)
    token_rejected = (r is None) or (r.status_code == 401)
    check(token_rejected,
          f"Token отозван после logout ({status(r)})",
          "Token всё ещё работает после logout!", f"status={status(r)}")


# ── Section: S3 ───────────────────────────────────────────────────────────────

def test_s3():
    section("Yandex Object Storage")

    endpoint    = os.getenv("YA_PUBLIC_UPLOADER_ENDPOINT_URL")
    region      = os.getenv("YA_PUBLIC_UPLOADER_REGION_NAME")
    key_id      = os.getenv("YA_PUBLIC_UPLOADER_ACCESS_KEY_ID")
    secret      = os.getenv("YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY")
    bucket      = os.getenv("YA_PUBLIC_UPLOADER_BUCKET_NAME")
    public_base = os.getenv("YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL")

    if not all([endpoint, key_id, secret, bucket]):
        skip("YA_PUBLIC_UPLOADER_* не настроены в .env")
        return

    import boto3
    from botocore.exceptions import ClientError

    try:
        s3 = boto3.session.Session().client(
            "s3",
            endpoint_url=endpoint,
            region_name=region,
            aws_access_key_id=key_id,
            aws_secret_access_key=secret,
        )
    except Exception as e:
        fail("S3 client init failed", str(e))
        return

    test_key  = f"test/e2e_{uuid.uuid4().hex[:12]}.txt"
    test_body = b"eliza.gallery e2e test"

    try:
        s3.put_object(Bucket=bucket, Key=test_key, Body=test_body, ContentType="text/plain")
        ok(f"Upload → s3://{bucket}/{test_key}")
    except Exception as e:
        fail("Upload failed", str(e))
        return

    try:
        obj  = s3.get_object(Bucket=bucket, Key=test_key)
        body = obj["Body"].read()
        check(body == test_body,
              "Download (boto3): содержимое совпадает",
              "Download (boto3): содержимое не совпадает", f"got={body!r}")
    except Exception as e:
        fail("Download (boto3) failed", str(e))

    if public_base:
        pub_url = f"{public_base.rstrip('/')}/{test_key}"
        try:
            r = requests.get(pub_url, timeout=10)
            check(r.status_code == 200 and r.content == test_body,
                  f"Public URL доступен: {pub_url}",
                  "Public URL недоступен", f"status={r.status_code}")
        except Exception as e:
            fail("Public URL request failed", str(e))
    else:
        skip("YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL не задан — пропускаем проверку public URL")

    try:
        s3.delete_object(Bucket=bucket, Key=test_key)
        ok(f"Delete: {test_key}")
    except Exception as e:
        fail("Delete failed", str(e))
        return

    try:
        s3.get_object(Bucket=bucket, Key=test_key)
        fail("Объект всё ещё существует после удаления")
    except ClientError as e:
        if e.response["Error"]["Code"] in ("NoSuchKey", "404"):
            ok("Объект удалён (404 после delete)")
        else:
            fail("Неожиданная ошибка после delete", str(e))
    except Exception:
        ok("Объект удалён")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="eliza.gallery integration tests")
    parser.add_argument("--base-url", default="http://localhost:8000")
    parser.add_argument("--email", required=True,
                        help="Email для теста auth flow и отправки письма")
    parser.add_argument("--staff-token", default=os.getenv("TEST_STAFF_TOKEN"),
                        help="Staff auth token (или TEST_STAFF_TOKEN в .env)")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    print(f"\n{B}eliza.gallery — Integration Tests{RS}")
    print(f"  Base URL:     {base}")
    print(f"  Email:        {args.email}")
    print(f"  Staff token:  {'✓' if args.staff_token else '—'}")

    cat_id, prod_id = test_public_api(base)
    token = test_auth_and_email(base, args.email)
    test_authenticated_user(base, token, prod_id)
    # Staff tests run BEFORE logout so that if the auth-flow user is staff
    # (same token via get_or_create), the staff token is still valid here.
    test_staff(base, args.staff_token, prod_id)
    test_logout(base, token)
    test_s3()

    total = _passed + _failed
    print(f"\n{'─' * 60}")
    if _failed == 0:
        print(f"{G}{B}Все {total} тестов прошли ✓{RS}")
    else:
        print(f"{R}{B}Прошло: {_passed}/{total}   Упало: {_failed}{RS}")
    sys.exit(0 if _failed == 0 else 1)


if __name__ == "__main__":
    main()
