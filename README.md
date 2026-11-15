# AnotaAI

## Sobre o Projeto

O AnotaAI é uma aplicação web que tem como objetivo auxiliar estudantes em seu dia a dia com as anotações feitas em aula.

O principal problema dos alunos se encontra na dificuldade de revisar suas anotações realizadas durante o fluxo das aulas, sendo elas por vezes confusas e mal organizadas. Pensando nisso, o AnotaAI surge com a proposta de utilizar a inteligência artificial para sanar essa situação.

Essa aplicação cria resumos, organiza os principais tópicos, gera flashcards, sugere correções de possíveis informações incorretas, tudo isso a partir da própria anotação. Dessa forma, ela atua como uma ferramenta de apoio e o estudante. Sendo importante ressaltar que é o aluno que possui a decisão final sobre as sugestões apresentadas, garantindo o protagonismo no aprendizado.

A versão atual permite o gerenciamento individual das anotações, organização de conteúdo anotado utilizando inteligência artificial generativa, verificação de inconsistências, criação de flashcards, consulta de métricas de desempenho e acesso à plataforma por meio de autenticação em dois fatores por e-mail. Também oferece recuperação de senha, edição de perfil e uma área administrativa com gestão de perfis de acesso e auditoria, além de solicitação de anonimização de dados cadastrais e exclusão dos materiais de estudo após aprovação administrativa.

## Equipe

- Carlos Henrique Costa França Junior
- Letícia de Souza Machado

## Funcionalidades implementadas

### Anotação:

- Cadastro de anotação.
- Edição de título e conteúdo.
- Visualização de anotação existente.
- Listagem de anotações.
- Exclusão da anotação com confirmação.
- Vinculação das anotações ao usuário autenticado.
- Acesso às anotações restrito ao proprietário.
- Ordenação das anotações pela última atualização.
- Filtro por título.
- Contador de caracteres.
- Organização de anotações com IA, incluindo resumo, palavras-chave, tópicos, conceitos e processos.
- Verificação de possíveis inconsistências e apresentação de sugestões de correção com fontes, sujeitas à avaliação do usuário.

### Histórico:

- Registro de versões do conteúdo na criação da anotação.
- Registro de versões do conteúdo após revisão por IA.
- Visualização prévia do conteúdo original no editor.
- Consulta do histórico de versões de anotações.

### Dashboard de desempenho:

- Quantidade de anotações do usuário.
- Total de caracteres e média de caracteres por anotação.
- Data da última atualização das anotações.
- Indicadores de desempenho e revisão de flashcards, conforme os registros do usuário.

### Flashcards e revisão

- Geração de flashcards a partir do conteúdo das anotações utilizando OpenRouter.
- Armazenamento de baralhos com perguntas e respostas vinculados ao usuário e à anotação.
- Registro de respostas corretas e incorretas para acompanhamento do estudo.
- Revisão direcionada aos flashcards respondidos incorretamente.
- Consulta e exclusão de baralhos.

### Cadastro e autenticação

- Cadastro com nome, e-mail, senha, data de nascimento e escolaridade.
- Validação de e-mail único e senha forte: pelo menos 12 caracteres, maiúscula, minúscula, número e símbolo. Há o bloqueio de sequências simples e limitação a 72 bytes.
- Cadastro restrito a maiores de 18 anos, com declaração de maioridade e aceite dos Termos de Uso.
- Registro da versão dos termos aceitos e das datas de aceite e declaração.
- Login com e-mail e senha seguido de código de seis dígitos enviado por e-mail.
- Código de login válido por 5 minutos, com limite de 5 tentativas e intervalo de 1 minuto entre solicitações.
- Recuperação de senha por código enviado por e-mail, válido por 10 minutos, com limite de 5 tentativas e intervalo de 1 minuto entre solicitações.
- Encerramento de sessão pela opção de sair.
- Senhas e códigos armazenados como hashes BCrypt, autenticação por sessão.

### Perfil e administração

- Consulta e edição de nome, e-mail e escolaridade.
- Histórico de atividades do usuário na página de perfil.
- Área administrativa restrita ao perfil `ADMIN`.
- Resumo com quantidade de usuários, anotações e registros de auditoria.
- Listagem de usuários e alteração dos perfis de acesso entre `USER` e `ADMIN`, sem permitir que o administrador altere o próprio perfil de acesso.
- Consulta administrativa dos registros de auditoria, incluindo ação, recurso, resultado, endereço IP e data.
- Solicitação de exclusão da conta pelo estudante: desativação imediata do acesso, registro do pedido e aviso aos administradores ativos.
- Conclusão da solicitação pelo administrador: exclusão de anotações, histórico de versões, baralhos e flashcards, além de códigos de autenticação e recuperação de senha. Anonimização dos dados cadastrais e desvinculação de registros técnicos associados à conta.
- Criação inicial de administrador por configuração externa.

### Demais implementações

- Página de Termos de Uso e Privacidade com informações sobre uso de serviços de terceiros, exclusão e anonimização de dados.
- Envio de códigos de autenticação e recuperação de senha pela Gmail API.
- Persistência no MongoDB com Spring Data MongoDB, incluindo usuários, anotações, auditoria e flashcards.
- Regras de mitigação de Prompt Injection: separação entre instruções e conteúdo das anotações, tratamento do texto como dados não confiáveis, validações das respostas JSON e identificação local de padrões suspeitos, sem novas chamadas à API.
- Restrição da busca web à verificação de inconsistências e validação dos formatos de resposta do OpenRouter.
- Testes unitários e de integração Java, testes de interface JavaScript (incluindo regressão de XSS) e pipeline de CI no GitHub Actions.
- Proteção contra inserção insegura de conteúdo de anotações na interface, utilizando elementos DOM de forma segura.

## Tech Stack

### Front End
- HTML5
- CSS3
- JavaScript
- Fetch API

### Backend
- Java 21
- Spring Boot 3.4.1
- Spring Web MVC, Spring Security

### Banco de Dados
- MongoDB
- Spring Data MongoDB

### Integrações

- Gmail API
- OpenRouter

### Build, testes e qualidade:
- Maven
- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- JaCoCo
- Checkstyle
- Node.js

## Como executar o projeto

### 1. Pré-requisitos

- JDK 21
- Maven
- MongoDB Atlas ou uma instância compatível do MongoDB
- Conta e chave de API válida da OpenRouter para gerar conteúdo com IA
- Integração com a Gmail API para autenticação por e-mail.

### 2. Clone e acesse o repositório
```bash
git clone https://github.com/lea-machado/anotaAI.git
cd anotaAI
```

### 3. Configure as credenciais
Confira as configurações disponíveis no arquivo:
`AnotaAI/src/main/resources/application.properties`




| Variável | Finalidade |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | String de conexão ao MongoDB |
| `MONGODB_DATABASE` | Nome do banco, padrão `anotaai` |
| `OPENROUTER_API_KEY` | Chave para acesso ao OpenRouter |
| `OPENROUTER_MODEL` | Modelo, padrão `openai/gpt-4o-mini-2024-07-18` |
| `GMAIL_CLIENT_ID` | Identificador do cliente OAuth 2.0 |
| `GMAIL_CLIENT_SECRET` | Segredo do cliente OAuth 2.0 |
| `GMAIL_REFRESH_TOKEN` | Token de atualização autorizado para enviar e-mails pela conta remetente |
| `GMAIL_SENDER_EMAIL` | Endereço da conta remetente autorizada |
| `ADMIN_BOOTSTRAP_EMAIL` | E-mail da conta administrativa inicial; sem esta variável o bootstrap é ignorado |
| `ADMIN_BOOTSTRAP_PASSWORD` | Senha inicial forte do administrador quando a aplicação precisa criá-lo |
| `ADMIN_BOOTSTRAP_RESET_PASSWORD` | Opcional. `true` redefinirá a senha do administrador existente |
| `PORT` | Porta HTTP, padrão `8080` |

#### 3.1 Configure o MongoDB
1. Crie um cluster nomeado **anotaai**.
2. Cadastre um usuário para o banco de dados.
3. Clique em Connect, defina a linguagem para Java e Client Libray como SpringData MangoDB.
4. Copie a URI de conexão. Substitua `<db_password>` pela senha do usuário do banco (caso haja caracteres especiais na senha, eles devem ser codificados para uma URL).
5. Abra um terminal na pasta raiz do projeto e configure o MangoDB com o seguinte comando:

**Windows**:

```powershell
$env:SPRING_DATA_MONGO_URI = "mongodb+srv://USUARIO:SENHA@anotaai.mongodb.net/"
```

**Linux ou macOS**:
```bash
export SPRING_DATA_MONGO_URI='mongodb+srv://USUARIO:SENHA@anotaai.mongodb.net/'
```

> Substitua o exemplo pela URI real obtida no Atlas (não esqueça de inserir sua senha na URI copiada).

#### 3.2 Configure o envio de e-mails

Defina as variáveis utilizadas pela integração com a Gmail API:

| `GMAIL_CLIENT_ID` | Identificador do cliente OAuth 2.0. |
| `GMAIL_CLIENT_SECRET` | Segredo do cliente OAuth 2.0. |
| `GMAIL_REFRESH_TOKEN` | Token de atualização autorizado para enviar e-mails pela conta remetente. |
| `GMAIL_SENDER_EMAIL` | Endereço da conta remetente autorizada. |

O envio de e-mails precisa funcionar para concluir o login, inclusive de administradores, e para recuperar senhas.

#### 3.3 Configure a chave API OpenRouter

Crie uma chave API na OpenRouter e a insira na variável de ambiente `OPENROUTER_API_KEY`.

#### 3.4 Crie o primeiro usuário admin

Crie o primeiro usuário admin através dos comandos abaixo (substitua 'EMAIL' e 'SENHA' por um endereço e-mail e senha que desejar):

**Windows**:

```powershell
$env:ADMIN_BOOTSTRAP_EMAIL = "EMAIL"
$env:ADMIN_BOOTSTRAP_PASSWORD = "SENHA"
```

**Linux ou macOS**:

```bash
export ADMIN_BOOTSTRAP_EMAIL='EMAIL'
export ADMIN_BOOTSTRAP_PASSWORD='SENHA'
```

### 4. Inicie a aplicação

Na pasta `AnotaAI`, execute:

```bash
mvn clean compile
mvn spring-boot:run
```
Por padrão, a aplicação é executada na porta 8080

Pela IDE, abra o projeto Maven e execute a classe `br.com.anotaai.AnotaAiApplication`, localizada em `AnotaAI/src/main/java/br/com/anotaai/AnotaAiApplication.java`.

### Acesse a aplicação pelo navegador
Acesse:

http://localhost:8080/

*Não abra os arquivos HTML diretamente com file:///.... O frontend utiliza caminhos relativos para a API (/api) e deve ser acessado conforme link acima*


## Observação

Novos cadastros recebem o perfil `USER`.

A conta administradora inicial é criada pela classe `AdministradorInicializador` apenas quando `ADMIN_BOOTSTRAP_EMAIL` e `ADMIN_BOOTSTRAP_PASSWORD` são configurados e a senha deve cumprir a política de segurança definida em `UsuarioService` (tamanho mínimo de 12 caracteres, ao menos uma letra maiúscula e uma minúscula, ao menos um número, ao menos um caractere especial).

Uma conta `USER` já existente com o mesmo e-mail não é promovida automaticamente. A senha de um administrador existente só é redefinida quando `ADMIN_BOOTSTRAP_RESET_PASSWORD=true`. Administradores acessam `/pages/admin.html`. Depois da criação, as variáveis de bootstrap podem ser removidas do ambiente.
