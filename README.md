# AnotaAI

## Sobre o Projeto

O AnotaAI consistirá em uma aplicação web que tem como objetivo auxiliar estudantes em seu dia a dia com as anotações feitas em aula.

O principal problema dos alunos se encontra na dificuldade de revisar o que foi escrito por ele durante o fluxo das aulas, sendo muito vezes confuso e mal organizado. Pensando nisso, o AnotaAI vem com a proposta de utilizar a inteligência artificial para sanar essa situação, criando resumos, títulos, organizando em principais tópicos e flashcards a partir da própria anotação.
O principal diferencial desse sistema é que ele atua apenas como uma ferramenta de apoio e o estudante que possui a decisão final sobre as sugestões apresentadas, garantindo o protagonismo no aprendizado.

Na fase atual do projeto é possível criar, editar, excluir e visualizar anotações. Além disso, há também a funcionalidade relacionada às métricas de anotações, exibindo quantidade de notas, quantidade de caracteres e última atualização dos dados.

## Equipe

- Carlos Henrique Costa França Junior
- Letícia de Souza Machado

## Funcionalidades implementadas em 14/09
### Anotação:
- Cadastro
- Edição de título e conteúdo
- Visualização de anotação existente
- Listagem de anotações
- Exclusão da anotação

### Histórico:
- Registro de versões das anotações

### Dashboard de desempenho:
- Métricas de quantidade de anotações, caracteres, média de caracteres por anotação e data da última atualização

### Demais implementações:
- Versionamento de banco de dados
- Interface do usuário para criação de anotações e visualização de suas métricas

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

### Inteligência Artificial
- Google Gemini API 

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

### 4. Inicie a aplicação
Execute a classe:

`src/main/java/br/com/anotaai/AnotaAiApplication.java`

Caso, possua o Maven instalado, também é possível realizar a execução no terminal:

```bash
mvn spring-boot:run
```
*Por padrão, a aplicação é executada na porta 8080*

### Acesse a aplicação pelo navegador
Acesse:

http://localhost:8080/

*Não abra os arquivos HTML diretamente com file:///.... O frontend utiliza caminhos relativos para a API (/api) e deve ser acessado conforme link acima*

## Observação

Este README será atualizado conforme a evolução do projeto.
