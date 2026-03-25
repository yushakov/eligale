#!/bin/bash
set -e

echo "==> git pull"
git pull

echo "==> pip install"
source venv/bin/activate
pip install -r requirements.txt

echo "==> migrate"
python manage.py migrate

echo "==> collectstatic"
python manage.py collectstatic --noinput

echo "==> restart gunicorn"
systemctl restart gunicorn

echo "==> done"
