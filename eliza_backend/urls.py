"""
URL configuration for eliza_backend project.

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/6.0/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path, include
from main.views import index, privacy, privacy_ru, termsofuse, termsofuse_ru, consent_ru, consent_en
from catalog.views import log_view

urlpatterns = [
    path("", include("uploader.urls")),
    path("", include("catalog.urls")),
    path("", include("users.urls")),
    path("", include("chat.urls")),
    path("privacy/", privacy, name="privacy"),
    path("privacy-ru/", privacy_ru, name="privacy_ru"),
    path("termsofuse/", termsofuse, name="termsofuse"),
    path("termsofuse-ru/", termsofuse_ru, name="termsofuse_ru"),
    path("consent-ru/", consent_ru, name="consent_ru"),
    path("consent-en/", consent_en, name="consent_en"),
    path("", index),
    path('admin/', admin.site.urls),
    path('log', log_view, name='log_view'),
]
