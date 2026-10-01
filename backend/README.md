# Telemicro API

API de orçamentos: Java 21, Spring Boot 4.1.1, Spring Security com JWT, Maven Wrapper, PostgreSQL 17 e Flyway. O frontend Angular continua na raiz do repositório.

## Executar localmente

Requisitos: JDK 21 ou superior, Docker Desktop em execução e acesso à internet no primeiro build.

No PowerShell, dentro de `backend/`:

```powershell
docker compose up -d --wait
.\scripts\Initialize-LocalConfig.ps1
.\scripts\Start-Local.ps1
```

`Initialize-LocalConfig.ps1` é executado somente na primeira configuração. Gera chave JWT e senha aleatórias em `.env.local`, ignorado pelo Git, para o usuário `admin@telemicro.local`. O script recusa sobrescrever um arquivo existente. `Start-Local.ps1` carrega esse arquivo como variáveis do processo e restaura o ambiente ao encerrar. Consulte o arquivo local para obter a senha; ela não é exibida nos logs.

Em Linux/macOS, configure as variáveis descritas abaixo e use `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. O perfil local também pode iniciar sem chave configurada, gerando uma chave temporária que invalida tokens ao reiniciar; não cria usuário automaticamente.

A API usa a porta 8080. O PostgreSQL fica acessível apenas nesta máquina em `localhost:55432`, banco/usuário `telemicro`, senha `telemicro_local_only`. São credenciais públicas exclusivamente locais; não reutilizar em hospedagem. A porta 55432 evita conflito com os outros bancos locais já existentes nesta máquina.

Verificar saúde:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

O retorno esperado é `status: UP`, incluindo a checagem da conexão ao banco, sem detalhes internos. A API disponibiliza catálogo e criação pública de pedidos, login e gestão administrativa protegida.

O Compose preserva os dados em volume. Para parar o banco sem apagar os dados:

```powershell
docker compose stop
```

## Configuração

### IntelliJ IDEA

Abra `backend/pom.xml` como projeto Maven, ou use **Add as Maven Project** no arquivo se abriu a raiz Angular. Depois execute **Reload All Maven Projects** na janela Maven.

Em **File → Project Structure**, selecione JDK 21 ou superior para Project SDK e Module SDK; Language level deve ser 21. Em **Settings → Build, Execution, Deployment → Compiler → Java Compiler**, o bytecode target do módulo `telemicro-api` deve ser 21. O POM declara release, source e target 21 explicitamente para a importação.

Se aparecer `configured for JVM target 5`, a configuração do módulo na IDE está desatualizada ou o projeto não foi importado como Maven. Reimporte o POM e confira o target; não é necessário instalar JDK 8. A compilação pelo Maven Wrapper usa a configuração do POM.

Para executar `TelemicroApiApplication` na IDE, informe `--spring.profiles.active=local` em Program arguments e configure as variáveis de `.env.local` no ambiente da execução. O Spring não lê esse arquivo automaticamente. Como alternativa, use `scripts/Start-Local.ps1`, que já prepara o ambiente. A porta 8080 deve estar disponível: encerre uma execução anterior antes de iniciar outra.

O perfil `local` fornece defaults para o banco do Compose. Sem esse perfil, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET` são obrigatórios. `PORT` é opcional, com default 8080. `JWT_SECRET` deve ser Base64 válido contendo pelo menos 32 bytes aleatórios; não usar texto de senha como chave.

Exemplo de configuração externa no PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://HOST:5432/DATABASE?sslmode=require'
$env:DB_USERNAME = 'DATABASE_USER'
$env:DB_PASSWORD = 'DATABASE_PASSWORD'
$env:JWT_SECRET = 'BASE64_RANDOM_KEY'
.\mvnw.cmd spring-boot:run
```

`.env.example` documenta as variáveis; Spring Boot não carrega `.env` automaticamente. Segredos devem ser configurados no ambiente de execução e não versionados. O frontend não deve receber credenciais de banco.

## Migrações

Flyway aplica automaticamente `src/main/resources/db/migration/` na inicialização:

- V1 cria usuários, catálogo, pedidos, histórico e notas, com FKs, checks e índices.
- V2 cadastra os sete serviços existentes no formulário.
- V3 cria o armazenamento das chaves de idempotência, associado aos pedidos.
- V4 adiciona `is_demo` aos pedidos. Pedidos existentes e novas submissões públicas permanecem privados (`false`).

Nenhum usuário/senha administrativa é criado por migração. O catálogo usa entidade e repositório JPA, com `ddl-auto=validate`. A escrita dos pedidos usa JDBC na mesma transação gerenciada pelo Spring/JPA para aproveitar locks e `ON CONFLICT` do PostgreSQL. O schema é criado exclusivamente pelo Flyway. Não alterar uma migração aplicada; adicionar uma nova versão.

Datas usam `timestamptz`; IDs são UUID; exclusões não se propagam automaticamente para o histórico.

## Endpoints públicos

`GET /api/v1/services` retorna somente serviços ativos, em ordem de exibição:

```json
[{"code":"TECHNICAL_ASSISTANCE","name":"Assistência técnica"}]
```

O exemplo acima é abreviado; o catálogo inicial tem sete opções. Enviar o `code` selecionado, não o nome de exibição.

`POST /api/v1/budgets` aceita JSON com `customerName`, `phone`, `serviceCode` e `message` opcional. Exemplo no PowerShell:

```powershell
$requestKey = [guid]::NewGuid().ToString()
$budgetBody = @{
    customerName = 'Cliente de demonstração'
    phone = '(33) 99999-9999'
    serviceCode = 'TECHNICAL_ASSISTANCE'
    message = 'Notebook não liga.'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/budgets `
    -Headers @{ 'Idempotency-Key' = $requestKey } `
    -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($budgetBody))
```

Sucesso retorna HTTP 201 com somente `protocol` e `createdAt` (UTC). O protocolo usa prefixo `TM-` e 32 caracteres hexadecimais. Dados pessoais e ID interno não são retornados. A criação também grava o status NEW e seu evento inicial.

Validação: nome obrigatório até 100 caracteres; telefone com DDD até 24 caracteres de entrada, normalizado para dígitos com prefixo 55; código de serviço ativo até 50 caracteres; mensagem até 2.000 caracteres. Nome, código e mensagem têm espaços externos removidos. Mensagem ausente, null ou vazia têm o mesmo significado.

### Retentativas e consistência

- Header `Idempotency-Key` obrigatório: 16 a 100 caracteres, letras ASCII, números, hífen ou sublinhado. UUID é recomendado.
- Gerar uma chave por envio lógico e reutilizá-la quando houver timeout ou falha de conexão. Novo pedido usa nova chave.
- Durante 24 horas, mesma chave e conteúdo normalizado retornam a resposta original, inclusive HTTP 201; nenhuma duplicata é criada. Isso continua válido se o serviço ficar inativo após a primeira criação.
- Mesma chave com conteúdo diferente retorna HTTP 409. Depois de 24 horas a chave pode gerar um novo pedido, portanto a janela de proteção é limitada.
- Locks transacionais no PostgreSQL serializam a mesma chave, inclusive em múltiplas instâncias da API. Pedido, histórico e chave são confirmados juntos ou revertidos juntos.
- Colisão de protocolo é tratada com `ON CONFLICT` e nova geração, em até cinco tentativas.
- Chaves vencidas são removidas quando reutilizadas. Uma rotina geral de limpeza poderá ser adicionada em operação; não há tarefa periódica mantendo o Neon ativo nesta etapa.

Erros retornam `application/problem+json`. Dados inválidos e serviço inexistente/inativo retornam 400; validação inclui `errors` por campo. A confirmação não representa orçamento financeiro aprovado. Não há consulta pública de detalhes nesta etapa.

## Verificação e build

Se a API estiver rodando pelo JAR em `target/`, pare essa execução antes do build; no Windows o arquivo fica bloqueado enquanto está em uso.

```powershell
.\mvnw.cmd verify
```

Os testes usam Testcontainers e PostgreSQL isolado, sem acessar o banco local ou dados reais. Docker deve estar disponível; a ausência dele provoca falha explícita. A suíte tem 31 testes: migrações, catálogo, criação, idempotência, JWT, autenticação, permissões ADMIN/DEMO, isolamento dos dados, paginação/filtros, transições, concorrência, rollback, CORS, limites de tentativas e bootstrap.

O JAR executável fica em `target/telemicro-api-0.0.1-SNAPSHOT.jar`:

```powershell
.\scripts\Start-Local.ps1 -UseJar
```

## Organização

Entrada: `br.com.telemicro.api.TelemicroApiApplication`. `servicecatalog` contém o catálogo JPA; `budget` contém contratos, criação e gestão; `auth` contém login e bootstrap; `config` contém Spring Security, JWT, CORS e limitação de tentativas; `shared` contém erros padronizados.

## Autenticação e configuração inicial

Spring Security Resource Server valida JWT HS256, assinatura, expiração, issuer e audience. Tokens duram 30 minutos, exigem subject/issuedAt/expiration/audience e usam o ID do usuário como subject. Toda requisição autenticada confere usuário ativo e papel atual no PostgreSQL. Desativar a conta bloqueia o token; alterar o papel muda imediatamente as permissões de novas requisições. Senhas são armazenadas com BCrypt (custo 12).

Para configurar o primeiro administrador fora do script local, usar `BOOTSTRAP_ENABLED=true`, `BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_PASSWORD` no processo Java. A senha exige pelo menos 12 caracteres e no máximo 72 bytes UTF-8. O bootstrap é transacional, serializado no banco e não redefine senha nem reativa contas. Recusa criar um administrador adicional se já houver outro. Depois da primeira execução, definir `BOOTSTRAP_ENABLED=false` e remover as variáveis da senha do ambiente. No arquivo local, manter a senha disponível para consulta pessoal até escolher um armazenamento próprio para suas credenciais.

Não há cadastro público, refresh token ou sessão de servidor. O cliente deve enviar `Authorization: Bearer TOKEN`, guardar token somente em memória e removê-lo no logout. Um token copiado continua válido até expirar enquanto a conta estiver ativa; revogação por sessão fica para evolução futura. CSRF está desabilitado porque a autenticação usa header explícito, sem cookies de autenticação. HTTPS é obrigatório na publicação.

`POST /api/v1/auth/login` recebe `email` e `password`. Retorna `accessToken`, `tokenType: Bearer`, `expiresIn: 1800` e `user` (id, email, role). Credenciais erradas, usuário inexistente ou inativo retornam a mesma mensagem 401. `GET /api/v1/auth/me` exige token e retorna somente o perfil atual.

Exemplo PowerShell sem imprimir token ou senha:

```powershell
$credentials = Get-Credential -UserName 'admin@telemicro.local' -Message 'Use a senha gerada em .env.local'
$loginBody = @{
    email = $credentials.UserName
    password = $credentials.GetNetworkCredential().Password
} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login `
    -ContentType 'application/json' -Body $loginBody
$authorization = @{ Authorization = "Bearer $($session.accessToken)" }
Invoke-RestMethod http://localhost:8080/api/v1/auth/me -Headers $authorization
```

## Gestão administrativa

Todas as rotas abaixo usam `/api/v1/admin/budgets` e retornam `Cache-Control: no-store`.

| Método e rota | Permissão | Uso |
| --- | --- | --- |
| GET `/` | ADMIN/DEMO | Lista paginada |
| GET `/summary` | ADMIN/DEMO | Contagem por status com os mesmos filtros |
| GET `/{id}` | ADMIN/DEMO | Pedido, histórico e notas |
| PATCH `/{id}/status` | ADMIN | Atualiza status com versão esperada |
| POST `/{id}/notes` | ADMIN | Acrescenta observação interna |

A rota de listagem é `/api/v1/admin/budgets`, sem barra final. Filtros: `page` (zero por padrão), `size` (20 por padrão, máximo 100), `status`, `serviceCode`, `q` (busca literal por nome ou protocolo, até 100 caracteres), `from`/`to` (instantes ISO-8601 com offset; início inclusivo e fim exclusivo) e `sort=NEWEST|OLDEST`. Ordenação desempata pelo ID. `from` deve ser anterior a `to`. Parâmetros e SQL são separados; ordenação usa somente valores permitidos. Retorno da lista: `content`, `page`, `size`, `totalElements`, `totalPages`. Summary retorna `counts` com os quatro status e `total`.

Detalhes retornam `budget`, `history` e `notes`. Histórico e notas incluem autor e data; o evento inicial não possui autor. Notas são somente inclusão, com limite de 2.000 caracteres. Não há exclusão de pedidos, edição ou remoção de histórico.

Atualizar status:

```json
{"status":"IN_PROGRESS","version":0}
```

Regras: NEW pode ir para IN_PROGRESS ou CANCELLED; IN_PROGRESS pode ir para COMPLETED ou CANCELLED; COMPLETED/CANCELLED podem ser reabertos para IN_PROGRESS, exigindo `justification`. Motivo fornecido é salvo como nota na mesma transação. Alteração incrementa `version`; cliente deve usar a nova versão na próxima operação. Versão desatualizada ou transição não permitida retorna 409. Repetir o status atual com a versão correta não gera histórico nem incrementa versão. Mudança, histórico e justificativa são atômicos. Duas alterações concorrentes com a mesma versão produzem um sucesso e um conflito.

Criar observação: `POST /{id}/notes` com `{"text":"Verificar fonte."}`, retornando 201. Pedido inexistente retorna 404; dados inválidos, 400; sem token válido, 401; sem permissão, 403.

## Demonstração e proteção de acesso

`DEMO_ENABLED=false` é o padrão: contas DEMO não podem entrar nem usar JWT emitido anteriormente. Para habilitar demonstração, configurar `DEMO_ENABLED=true`; opcionalmente criar a conta DEMO pelo bootstrap com `BOOTSTRAP_DEMO_EMAIL` e `BOOTSTRAP_DEMO_PASSWORD`, usando e-mail diferente do ADMIN.

DEMO é somente leitura e só consegue listar, resumir e consultar pedidos explicitamente marcados `is_demo=true` no preparo dos dados fictícios. Tentativas de consultar um pedido privado retornam 404. Pedidos existentes e novas submissões públicas sempre têm `is_demo=false`, mesmo no modo demonstrativo; assim os visitantes não expõem suas informações uns aos outros. O preparo do catálogo de pedidos fictícios será implementado na etapa de publicação. Usar banco separado da operação real.

Limites por IP: 10 tentativas de login e 20 submissões públicas a cada 15 minutos, configuráveis por `LOGIN_RATE_LIMIT`/`SUBMISSION_RATE_LIMIT`. Sucessos, falhas e retentativas contam. As categorias são independentes. Excesso retorna 429 com `Retry-After` em segundos. O armazenamento é em memória, limitado a 10 mil chaves; reiniciar zera contadores e múltiplas instâncias têm contadores separados. Quando cheio, novas chaves são temporariamente bloqueadas em vez de liberar limites antigos.

Por padrão a API usa o endereço da conexão e ignora cabeçalhos forwarded enviados pelo cliente. Na publicação atrás de proxy, conferir o modo seguro de obter o IP real do provedor antes de ativar confiança em forwarded headers, evitando que todas as pessoas compartilhem o limite do proxy.

CORS permite somente origens explícitas em `CORS_ALLOWED_ORIGINS`, separadas por vírgula. O perfil local permite `http://localhost:4200`; fora dele não há origem liberada por padrão. Wildcards são recusados na inicialização. Não são usados cookies nem `allowCredentials`. Preflight libera GET/POST/PATCH e os headers Authorization, Content-Type e Idempotency-Key. As rotas desconhecidas são bloqueadas por padrão.

Referência: [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

Planejamento completo em `../docs/PLANEJAMENTO.md`. O provisionamento Render/Neon/Vercel e a imagem Docker da API entram na etapa de publicação.
