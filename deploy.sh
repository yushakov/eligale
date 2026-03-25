#!/bin/bash
set -e

echo "==> git pull"
git pull

cd /home

echo "==> pip install"
source venv/bin/activate

cd eliza_app
pip install -r requirements.txt

echo "==> migrate"
python manage.py migrate

echo "==> collectstatic"
python manage.py collectstatic --noinput

echo "==> restart gunicorn"
systemctl restart gunicorn

echo "==> done"
