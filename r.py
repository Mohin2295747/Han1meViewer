import requests

# Replace with your actual DeepL API key
API_KEY = "ea5b8f25-b6c0-4d5d-913a-8ac28093afbe:fx"

# DeepL API endpoint
URL = "https://api-free.deepl.com/v2/translate"  # or "https://api.deepl.com/v2/translate" for pro

# Text to translate
text_to_translate = "你好，世界！"

# Headers with new auth method
headers = {
    "Authorization": f"DeepL-Auth-Key {API_KEY}"
}

# Request payload
payload = {
    "text": text_to_translate,
    "source_lang": "ZH",  # Chinese
    "target_lang": "EN"   # English
}

# Send POST request
response = requests.post(URL, headers=headers, data=payload)

# Print result
if response.status_code == 200:
    result = response.json()
    print("Original:", text_to_translate)
    print("Translated:", result["translations"][0]["text"])
else:
    print("Error:", response.status_code, response.text)
