import requests
import json

# 1. Nosso "cartão de membro" (o token)
token = "abc123456789"

# 2. O endereço do "correio" (a URL da API)
url = f"https://swagger.jmmaster.net/api/sms/send?token={token}"

# 3. Nossa "cartinha" (a mensagem)
mensagem = {
  "user": "criança_hacker",
  "contact": [
    {
      "number": "5511987654321",
      "message": "Oi! Isso é um teste feito por uma criança muito inteligente!"
    }
  ],
  "type": 2
}

# 4. Enviando a mensagem para a API
# A gente usa uma "ferramenta" chamada 'requests' para fazer o envio
# A gente também transforma a nossa mensagem em um formato que a API entende (JSON)
resposta = requests.post(url, data=json.dumps(mensagem), headers={'Content-Type': 'application/json'})

# 5. Vendo o que a API respondeu
print("A API respondeu:")
print(resposta.json())
