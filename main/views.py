from django.shortcuts import render

def index(request):
    return render(request, 'index.html')

def privacy(request):
    return render(request, 'privacy.html')

def privacy_ru(request):
    return render(request, 'privacy_ru.html')

def termsofuse(request):
    return render(request, 'termsofuse.html')

def termsofuse_ru(request):
    return render(request, 'termsofuse_ru.html')

def consent_ru(request):
    return render(request, 'consent_ru.html')

def consent_en(request):
    return render(request, 'consent_en.html')
