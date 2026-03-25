#!/bin/bash
set -e

cd /home/eliza_app

echo "==> git pull"
git pull

echo "==> pip install"
source /home/venv/bin/activate
pip install -r requirements.txt

echo "==> migrate"
python manage.py migrate

echo "==> collectstatic"
python manage.py collectstatic --noinput

echo "==> restart gunicorn"
systemctl restart gunicorn

echo "==> done"
