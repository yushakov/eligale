function getCookieApk(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

async function apkUpload(input, fieldId, statusId) {
    const file = input.files[0];
    if (!file) return;
    const statusEl = document.getElementById(statusId);
    const fieldEl = document.getElementById(fieldId);
    statusEl.textContent = 'Загружаю... (это может занять минуту)';
    statusEl.style.color = '#666';
    try {
        const formData = new FormData();
        formData.append('filename', file.name);
        formData.append('content_type', 'application/vnd.android.package-archive');
        formData.append('prefix', 'apk');
        const presignResp = await fetch('/api/catalog/upload/presign', {
            method: 'POST',
            body: formData,
            headers: {'X-CSRFToken': getCookieApk('csrftoken')},
            credentials: 'same-origin',
        });
        const presignData = await presignResp.json();
        if (!presignResp.ok) throw new Error(presignData.error || 'Presign failed');
        const uploadResp = await fetch(presignData.upload_url, {
            method: 'PUT',
            headers: {'Content-Type': 'application/vnd.android.package-archive'},
            body: file,
        });
        if (!uploadResp.ok) throw new Error('Upload failed: ' + uploadResp.status);
        fieldEl.value = presignData.object_key;
        statusEl.textContent = `Загружено! Ключ: ${presignData.object_key}`;
        statusEl.style.color = 'green';
    } catch(e) {
        statusEl.textContent = 'Ошибка: ' + e.message;
        statusEl.style.color = 'red';
    }
}
