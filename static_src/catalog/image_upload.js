function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

async function catalogUpload(input, fieldId, previewId, statusId) {
    const file = input.files[0];
    if (!file) return;
    const statusEl = document.getElementById(statusId);
    const fieldEl = document.getElementById(fieldId);
    const previewEl = document.getElementById(previewId);
    statusEl.textContent = 'Загружаю...';
    statusEl.style.color = '#666';
    try {
        const formData = new FormData();
        formData.append('filename', file.name);
        formData.append('content_type', file.type || 'application/octet-stream');
        const presignResp = await fetch('/api/catalog/upload/presign', {
            method: 'POST',
            body: formData,
            headers: {'X-CSRFToken': getCookie('csrftoken')},
            credentials: 'same-origin',
        });
        const presignData = await presignResp.json();
        if (!presignResp.ok) throw new Error(presignData.error || 'Presign failed');
        const uploadResp = await fetch(presignData.upload_url, {
            method: 'PUT',
            headers: {'Content-Type': presignData.content_type},
            body: file,
        });
        if (!uploadResp.ok) throw new Error('Upload failed: ' + uploadResp.status);
        fieldEl.value = presignData.object_key;
        previewEl.src = presignData.public_url;
        previewEl.style.display = 'block';
        statusEl.textContent = 'Загружено!';
        statusEl.style.color = 'green';
    } catch(e) {
        statusEl.textContent = 'Ошибка: ' + e.message;
        statusEl.style.color = 'red';
    }
}
