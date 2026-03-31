#!/bin/bash
set -e

# Read APP_FOLDER from .env
APP_FOLDER=$(grep '^APP_FOLDER=' .env | cut -d'=' -f2-)
if [ -z "$APP_FOLDER" ]; then
    echo "ERROR: APP_FOLDER not set in .env"
    exit 1
fi

echo "==> git pull"
git pull

echo "==> pip install"
source "$APP_FOLDER/venv/bin/activate"

cd "$APP_FOLDER/eliza_app"
pip install -r requirements.txt

echo "==> migrate"
python manage.py migrate

echo "==> collectstatic"
python manage.py collectstatic --noinput

echo "==> restart gunicorn"
sudo systemctl restart gunicorn

echo "==> done"
