# Publicacao Telemicro: HostGator + Render + Neon

## Ordem de publicacao

1. Criar um banco dedicado no Neon, PostgreSQL 17, numa regiao proxima/igual a do Render.
2. Publicar a API no Render usando o Dockerfile em `backend/`.
3. Criar o primeiro administrador e verificar login/catalogo/CORS.
4. Verificar o dominio no Resend, ativar as notificacoes e testar a entrega.
5. Gerar o ZIP do Angular com a URL real da API e publicar apenas na pasta da Telemicro.
6. Validar o fluxo inteiro em HTTPS antes de entregar o acesso administrativo.

## Neon

- Usar um banco novo de producao. Os pedidos ficticios e usuarios locais nao precisam ser copiados.
- Em Connect, obter host, nome do banco, usuario e senha. Preferir uma conexao direta (sem `-pooler`) neste primeiro deploy: a API ja possui pool Hikari pequeno, e as migracoes Flyway usam essa mesma conexao.
- Configurar no Render, em variaveis separadas:
  - `DB_URL=jdbc:postgresql://HOST_REAL/NOME_DO_BANCO?sslmode=require`
  - `DB_USERNAME=USUARIO_REAL`
  - `DB_PASSWORD=SENHA_REAL`
- A URL JDBC nao deve conter usuario/senha. A URL `postgresql://usuario:senha@...` copiada do painel precisa ser separada nesses campos; nao basta colar essa URL em `DB_URL`.
- Flyway cria as tabelas e cadastra o catalogo automaticamente no primeiro inicio. Nao executar SQL de criacao de tabelas manualmente.
- Manter um backup externo com restauracao testada. O historico curto do plano gratuito nao substitui o backup operacional.

## Render

Os arquivos devem estar no repositorio conectado ao Render. O `render.yaml` oferece um Blueprint sem segredos, ou a criacao manual pode usar:

| Campo | Valor |
| --- | --- |
| Tipo | Web Service |
| Runtime | Docker |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` |
| Instance Type | Free |
| Health Check Path | `/actuator/health` |

O container compila e executa Java 21, usa a porta `PORT` e roda sem privilegios de root. Nao incluir `.env.local`, backups ou credenciais na imagem. O build da imagem nao executa testes de integracao; `mvnw verify` deve passar antes de publicar.

Variaveis iniciais:

| Variavel | Valor |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Credenciais do banco dedicado no Neon |
| `JWT_SECRET` | Base64 de pelo menos 32 bytes aleatorios; diferente da chave local e mantida entre deploys |
| `CORS_ALLOWED_ORIGINS` | `https://telemicroinformatica.com.br,https://www.telemicroinformatica.com.br` |
| `DEMO_ENABLED` | `false` |
| `BOOTSTRAP_ENABLED` | `true` apenas para criar o primeiro administrador |
| `BOOTSTRAP_ADMIN_EMAIL` | E-mail escolhido para o administrador |
| `BOOTSTRAP_ADMIN_PASSWORD` | Senha nova, minimo 12 caracteres e maximo 72 bytes UTF-8 |
| `MAIL_ENABLED` | `false` ate configurar o remetente e destinatario |
| `MAIL_PROVIDER` | `resend` |
| `MAIL_INTERVAL_MS` | `60000` |
| `MAIL_PANEL_URL` | `https://telemicroinformatica.com.br/admin` |

Ao confirmar o primeiro login, definir `BOOTSTRAP_ENABLED=false` e remover `BOOTSTRAP_ADMIN_PASSWORD` do ambiente. Nao usar o perfil `local` no Render.

Antes do site publico: health deve retornar UP; catalogo deve responder; login deve funcionar; API deve aceitar CORS apenas nas duas origens do site. Conferir o consumo de memoria do plano Free e ajustar a JVM se necessario. O Dockerfile reserva 256 MB para o heap, com margem para metaspace e memoria nativa; isso nao garante que toda carga caiba em 512 MB.

O rate limit atualmente utiliza o IP da conexao e ignora headers forwarded. No Render ele pode contabilizar varias pessoas no mesmo IP do proxy. Antes da abertura publica, validar o encaminhamento real e a resistencia a headers falsificados, e ajustar a confianca no proxy conforme o ambiente confirmado. Nao habilitar confianca irrestrita em headers enviados pelo navegador.

## Resend

O Render Free bloqueia SMTP nas portas 25, 465 e 587. Usar o envio HTTPS implementado, sem senha da caixa de e-mail.

1. Criar a conta e adicionar o dominio de envio. Preferir um subdominio dedicado, por exemplo `avisos.telemicroinformatica.com.br`, para separar a configuracao de envio das caixas atuais.
2. Adicionar somente os registros DNS indicados pelo Resend para esse subdominio. Preservar os registros MX e SPF das caixas atuais; nao criar dois SPF no mesmo nome nem substituir registros do laboratorio.
3. Aguardar o status Verified e criar uma API key com permissao de envio restrita ao dominio.
4. Configurar no Render:
   - `RESEND_API_KEY`: chave privada do Resend.
   - `MAIL_FROM`: endereco do dominio verificado, por exemplo `Telemicro <notificacoes@avisos.telemicroinformatica.com.br>`.
   - `MAIL_TO`: e-mail real que a equipe confirmou para receber avisos.
   - `MAIL_ENABLED=true`.
5. Criar um pedido identificado como teste pelo formulario e verificar protocolo, painel e caixa de entrada (incluindo spam). O sucesso da API de envio significa aceite do provedor; a entrega precisa ser conferida.

A fila existente preserva avisos entre reinicios e tenta novamente apos falha. O Resend recebe uma chave estavel por pedido e deduplica por 24 horas; nao e uma garantia de exatamente um envio para sempre. O e-mail contem protocolo, servico e link do painel, sem telefone, nome ou mensagem do cliente.

## HostGator

O backup atual ja foi feito pelo usuario. Confirmar que o ZIP pode ser aberto antes da troca.

Depois de obter a URL real do Render, na raiz do projeto:

```powershell
./deployment/hostgator/Build-Package.ps1 -ApiUrl 'https://NOME_REAL.onrender.com/api/v1'
```

O script gera um ZIP em `release/` com somente arquivos publicos do Angular, configuracao da URL e `.htaccess`. Nenhum arquivo do backend, senha ou chave deve entrar nesse pacote.

- Publicar o conteudo do ZIP diretamente na raiz de `telemicroinformatica.com.br` (sem uma pasta `browser` intermediaria).
- Preservar `.well-known` e atuar apenas na pasta da Telemicro. `/public_html` pertence ao laboratorio.
- O `.htaccess` permite abrir/recarregar `/admin` e `/admin/login` e mantem HTTPS. Validar essas rotas na hospedagem real.
- A URL publica fica em `assets/config/api-config.js`; a configuracao padrao do desenvolvimento continua `/api/v1` com o proxy local.
- Validar servicos, envio/protocolo, notificacao, login, status e alteracao de senha. Depois testar uma solicitacao apos o Render ficar inativo, sem criar monitor de pings para impedir a pausa.

## Limites do plano gratuito

Render pausa a API apos 15 minutos sem acessos; a retomada pode demorar cerca de um minuto ou mais. O frontend ja aguarda ate 90 segundos para envio e login, mas nenhuma duracao de retomada e garantida. O catalogo tambem depende da API estar disponivel.

Enquanto a API esta pausada, a fila nao envia mensagens nem executa novas tentativas. Avisos pendentes retornam ao processamento quando o servico voltar. A consulta periodica da fila pode manter o Neon ativo enquanto o Render estiver ativo, consumindo a cota de computacao do banco. Acompanhar as cotas reais nas contas dos provedores.

Referencias: [Render Docker](https://render.com/docs/docker), [Render Free](https://render.com/docs/free), [Neon conexoes](https://neon.com/docs/connect/connect-intro), [Resend envio](https://resend.com/docs/api-reference/emails/send-email), [Resend idempotencia](https://resend.com/docs/dashboard/emails/idempotency-keys).

## Validacao local — 11/10/2026

- 41 testes de backend passaram, com PostgreSQL isolado e envio de e-mail simulado. A suite completa passou antes do ultimo ajuste de health; as suites afetadas (17 testes) foram repetidas depois da correcao, sem falhas ou testes ignorados.
- 16 testes de frontend passaram no ChromeHeadless; build de producao passou.
- O ZIP de teste continha index, configuracao publica e .htaccess, sem arquivos privados; o pacote com URL ficticia foi removido. Gerar o pacote real somente depois de obter a URL da API publicada.
- A imagem final compilou e iniciou com Java 21, 1 CPU e limite de 512 MB. Health UP, sete servicos no catalogo, consumo observado de 270,1 MiB depois da inicializacao. Esse valor e uma observacao em repouso, nao um teste de carga.
- A verificacao SMTP do Actuator foi desativada: falhas do provedor de e-mail sao tratadas pela fila e nao devem impedir readiness da API. A checagem de conexao ao banco continua ativa.
- O teste `Test-RenderContainer.ps1` utiliza banco/rede temporarios e remove os containers ao concluir. Os dados locais da loja nao entram nesse teste.
- Ainda pendentes: criacao/configuracao dos servicos externos, envio dos arquivos ao repositorio conectado, administrador de producao, DNS/Resend, IP confiavel do proxy, entrega real de e-mail, publicacao na HostGator e verificacao do fluxo com pausa/retomada do Render.
