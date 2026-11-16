# Credenciais do OpenRouter no AnotaAI

O AnotaAI usa a chave de API do OpenRouter para gerar material de estudo. Gere as credenciais nos painéis oficiais e configure as variáveis no processo que inicia o backend.

## Chave do OpenRouter

1. **Abra a página de chaves.** Entre no painel do OpenRouter, acesse **API Keys** e escolha **Create API Key**.

2. **Identifique a chave.** Use um nome como `AnotaAI local`. Se quiser controlar os gastos, configure um limite de créditos e o período de renovação da chave.

3. **Copie a chave gerada.** Guarde o valor completo assim que ele aparecer e configure `OPENROUTER_API_KEY`. O painel exibe o valor completo apenas na criação.

## Configure o AnotaAI no Windows

No **IntelliJ IDEA**, abra `Run → Edit Configurations` e adicione a variável em **Environment variables** na configuração que inicia o AnotaAI.

Se iniciar pelo terminal, execute o bloco no **PowerShell 7** a partir da pasta `AnotaAI` e informe os valores quando solicitado:

```powershell
$env:OPENROUTER_API_KEY = Read-Host 'Chave do OpenRouter' -MaskInput

mvn spring-boot:run
```

Reinicie o backend depois de alterar as variáveis. O Spring Boot não carrega o arquivo `.env` por padrão.

> 🔒 **Segurança:** Mantenha o segredo do cliente, o refresh token e a chave estritamente fora do Git e de logs.