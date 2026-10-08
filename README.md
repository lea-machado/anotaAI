# AnotaAI

## Sobre o Projeto

O AnotaAI consistirá em uma aplicação web que tem como objetivo auxiliar estudantes em seu dia a dia com as anotações feitas em aula.

O principal problema dos alunos se encontra na dificuldade de revisar o que foi escrito por ele durante o fluxo das aulas, sendo muito vezes confuso e mal organizado. Pensando nisso, o AnotaAI vem com a proposta de utilizar a inteligência artificial para sanar essa situação, criando resumos, títulos, organizando em principais tópicos e flashcards a partir da própria anotação. O principal diferencial desse sistema é que ele atua apenas como uma ferramenta de apoio e o estudante que possui a decisão final sobre as sugestões apresentadas, garantindo o protagonismo no aprendizado.

A versão atual permite gerenciar anotações individuais, consultar métricas de desempenho e acessar a plataforma com autenticação em dois fatores por e-mail. Também oferece recuperação de senha, edição de perfil e uma área administrativa com gestão de perfis de acesso e auditoria.


## Equipe

- Carlos Henrique Costa França Junior
- Letícia de Souza Machado

## Funcionalidades da entrega 28/09

### Anotação:

- Cadastro de anotação.
- Edição de título e conteúdo.
- Visualização de anotação existente.
- Listagem de anotações.
- Exclusão da anotação com confirmação.
- Vinculação das anotações ao usuário autenticado.
- Acesso às anotações restrito ao proprietário.
- Ordenação das anotações pela última atualização.
- Filtro por título disponível na API.
- Contador de caracteres no editor.

### Histórico:

- Registro de versões do conteúdo na criação da anotação.
- Registro de versões do conteúdo a cada atualização da anotação.
- Visualização prévia do conteúdo original no editor.

### Dashboard de desempenho:

- Quantidade de anotações do usuário.
- Total de caracteres e média de caracteres por anotação.
- Data da última atualização das anotações.

### Cadastro e autenticação

- Cadastro com nome, e-mail, senha, data de nascimento e escolaridade.
- Validação de e-mail único e senha com pelo menos 8 caracteres e no máximo 72 bytes.
- Cadastro restrito a maiores de 18 anos, com declaração de maioridade e aceite dos Termos de Uso.
- Registro da versão dos termos aceitos e das datas de aceite e declaração.
- Login com e-mail e senha seguido de código de seis dígitos enviado por e-mail.
- Código de login válido por 5 minutos, com limite de 5 tentativas e intervalo de 1 minuto entre solicitações.
- Recuperação de senha por código enviado por e-mail, válido por 10 minutos, com limite de 5 tentativas e intervalo de 1 minuto entre solicitações.
- Encerramento de sessão pela opção de sair.
- Senhas e códigos armazenados como hashes BCrypt, autenticação por sessão 

### Perfil e administração

- Consulta e edição de nome, e-mail e escolaridade.
- Histórico de atividades do usuário na página de perfil.
- Área administrativa restrita ao perfil `ADMIN`.
- Resumo com quantidade de usuários, anotações e registros de auditoria.
- Listagem de usuários e alteração dos perfis de acesso entre `USER` e `ADMIN`, sem permitir que o administrador altere o próprio perfil de acesso.
- Consulta administrativa dos registros de auditoria, incluindo ação, recurso, resultado, endereço IP e data.

### Demais implementações

- Páginas de Termos de Uso e Política de Privacidade.
- Envio de códigos de autenticação e recuperação de senha pela Gmail API.
- Migrações do banco de dados com Flyway, incluindo usuários, auditoria, códigos de verificação e aceite dos termos.
- Testes unitários dos serviços de usuários, administração, autenticação em dois fatores e recuperação de senha.

## Tech Stack

### Front End
- HTML5
- CSS3
- JavaScript
- Fetch API

### Backend
- Java 21
- Spring Boot
- Maven

### Banco de Dados
- PostgreSQL
- Flyway

### Integrações

- Gmail API 

## Como executar o projeto:

### 1. Pré-requisitos:
- Java 21
- PostgreSQL
- Maven (caso a IDE não faça o gerenciamento do Maven automaticamente)

### 2. Clone e acesse a branch com a entrega atual do repositório
```bash
git clone https://github.com/lea-machado/anotaAI.git
cd anotaAI
git switch entrega1409
```

### 3. Configure o PostgreSQL
Crie o banco de dados nomeado como **anotaai** e confirme se os dados correspondem ao arquivo em:

`src/main/resources/application.properties`

Caso não, altere as informações no arquivo para corresponderem com as suas credenciais.

Configure o envio de e-mails

Defina as variáveis utilizadas pela integração com a Gmail API:

| `GMAIL_CLIENT_ID` | Identificador do cliente OAuth 2.0. |
| `GMAIL_CLIENT_SECRET` | Segredo do cliente OAuth 2.0. |
| `GMAIL_REFRESH_TOKEN` | Token de atualização autorizado para enviar e-mails pela conta remetente. |
| `GMAIL_SENDER_EMAIL` | Endereço da conta remetente autorizada. |

O envio de e-mails precisa funcionar para concluir o login, inclusive de administradores, e para recuperar senhas.

### 4. Inicie a aplicação

Na pasta `AnotaAI`, execute:

```bash
mvn spring-boot:run
```
Por padrão, a aplicação é executada na porta 8080

Pela IDE, abra o projeto Maven e execute a classe `br.com.anotaai.AnotaAiApplication`, localizada em `AnotaAI/src/main/java/br/com/anotaai/AnotaAiApplication.java`.

### Acesse a aplicação pelo navegador
Acesse:

http://localhost:8080/

Novos cadastros recebem o perfil `USER`. A migração `V4__criar_usuario_administrador.sql` cria uma conta administrativa inicial com a senha `12345678`. Antes de iniciar a aplicação pela primeira vez, abra o arquivo `AnotaAI/src/main/resources/db/migration/V4__criar_usuario_administrador.sql` e substitua o e-mail presente nele pelo seu, para receber o código de autenticação. Essa alteração deve ser feita antes de a migração V4 ser aplicada ao banco. Depois, entre com o e-mail informado e a senha `12345678`. Administradores podem acessar `/pages/admin.html` e gerenciar os perfis dos demais usuários.

*Não abra os arquivos HTML diretamente com file:///.... O frontend utiliza caminhos relativos para a API (/api) e deve ser acessado conforme link acima*

## Observação

Este README será atualizado conforme a evolução do projeto.
