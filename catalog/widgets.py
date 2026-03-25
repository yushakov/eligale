from django import forms
from django.conf import settings


class ImageUploadWidget(forms.TextInput):

    class Media:
        js = ('catalog/image_upload.js',)

    def render(self, name, value, attrs=None, renderer=None):
        key = value or ''
        public_url = ''
        if key:
            base = settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')
            public_url = f'{base}/{key}'

        field_id = attrs.get('id', f'id_{name}') if attrs else f'id_{name}'
        preview_id = f'{field_id}_preview'
        status_id = f'{field_id}_status'

        preview_style = 'max-width:200px;max-height:200px;display:block;margin-top:8px;border-radius:4px;'
        if not public_url:
            preview_style += 'display:none;'

        return f'''
<div style="display:flex;flex-direction:column;gap:4px;">
  <div style="display:flex;align-items:center;gap:8px;">
    <input type="text" name="{name}" value="{key}" id="{field_id}"
           style="flex:1;font-family:monospace;font-size:12px;" readonly>
    <label style="cursor:pointer;padding:4px 10px;background:#417690;color:white;border-radius:4px;font-size:13px;">
      Загрузить
      <input type="file" accept="image/*" style="display:none;"
             onchange="catalogUpload(this, '{field_id}', '{preview_id}', '{status_id}')">
    </label>
  </div>
  <span id="{status_id}" style="font-size:12px;color:#666;"></span>
  <img id="{preview_id}" src="{public_url}" style="{preview_style}" alt="">
</div>
'''
