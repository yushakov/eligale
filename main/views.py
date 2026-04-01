from django.shortcuts import render

def index(request):
    return render(request, 'index.html')

def privacy(request):
    return render(request, 'privacy.html')

def privacy_ru(request):
    return render(request, 'privacy_ru.html')
