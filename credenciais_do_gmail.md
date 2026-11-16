# Credenciais do Gmail

O AnotaAI usa o `refresh token` do Gmail para enviar códigos por e-mail.

Gere a credencial no painel oficial e configure as variáveis no processo que inicia o backend.

https://cloud.google.com/

## Gmail API

### Configure o projeto no Google Cloud

1. **Ative a Gmail API:** No Google Cloud Console, crie ou selecione um projeto. Em APIs e serviços, abra a Biblioteca, encontre **Gmail API** e clique em **Ativar**.
2. **Configure o consentimento OAuth:** No Google Auth Platform, preencha **Branding** com o nome do app e os contatos solicitados. Em **Audience**, para um teste com conta Gmail pessoal, use **External**. Se estiver em *Testing*, adicione a conta remetente em **Test users**.
3. **Adicione a permissão de envio:** Em **Data Access**, adicione o escopo abaixo. Esse endereço é um identificador da permissão de envio do Gmail. Copie-o como texto para o campo indicado no passo 6.
   ```text
   https://www.googleapis.com/auth/gmail.send
   ```
4. **Crie o cliente OAuth:** Em **Clients**, clique em **Create client** e selecione **Web application**. Adicione a URI abaixo em **Authorized redirect URIs**. Guarde o *Client ID* e o *Client secret* gerados para esse cliente.
   ```text
   https://developers.google.com/oauthplayground
   ```

---

## Gere o refresh token no OAuth Playground

5. **Use as credenciais do seu projeto:** Abra o [OAuth Playground](https://developers.google.com/oauthplayground) e clique na engrenagem. Marque **Use your own OAuth credentials**, informe o *Client ID* e o *Client secret* e mantenha *Access type* em **Offline**.
6. **Autorize a conta remetente:** No OAuth Playground, vá a **Step 1** e cole o endereço completo do passo 3 no campo **Input your own scopes**. Clique em **Authorize APIs**, entre na conta remetente do AnotaAI e conceda a permissão.
7. **Troque o código pelos tokens:** No **Step 2**, clique em **Exchange authorization code for tokens**. Copie o campo **Refresh token** para `GMAIL_REFRESH_TOKEN`. Use o e-mail da mesma conta em `GMAIL_SENDER_EMAIL`. O backend usa o refresh token para obter novos *access tokens*.

> ⚠️ **Atenção:** Em um app *External* com status *Testing*, o refresh token com o escopo do Gmail expira em **sete dias**. Para uso contínuo, confira as condições de publicação e verificação no Google Auth Platform antes de gerar o token definitivo.
