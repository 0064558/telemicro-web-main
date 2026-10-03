# Telemicro Informática

Landing page em Angular 20 standalone para a loja de informática em São João Evangelista, MG.

## Backend de orçamentos

A API Java/Spring Boot está em [`backend/`](backend/README.md), com PostgreSQL, Flyway, pedidos com protocolo/idempotência, JWT via Spring Security e gestão administrativa com permissões ADMIN/DEMO. O formulário e o painel Angular já estão integrados. Consulte o [planejamento e roadmap](docs/PLANEJAMENTO.md). A próxima etapa prepara Docker, dados de demonstração e hospedagem.

## Executar

Use Node.js compatível com Angular 20 (o projeto foi verificado com Node 24) e npm.

```bash
npm ci
npm start
```

Abra http://localhost:4200/. Para gerar a versão de produção:

```bash
npm run build
```

Os arquivos prontos ficam em `dist/telemicro-web/browser`. A entrada da aplicação é `src/index.html`. O antigo site estático na raiz e seus arquivos duplicados foram removidos. Este trabalho não inclui publicação.

Para usar o formulário e o painel, inicie o banco e a API seguindo `backend/README.md`, depois execute `npm start`. O proxy de desenvolvimento encaminha `/api` para `http://localhost:8080`; não é necessário alterar CORS para esse fluxo. Entre em `http://localhost:4200/admin` com `admin@telemicro.local` e a senha de `backend/.env.local` criada pelo script local. Esse arquivo é ignorado pelo Git.

`API_URL` em `src/app/core/api.ts` define a base pública da API (padrão `/api/v1`). Na publicação, configure a URL HTTPS da API Render ou um encaminhamento equivalente na Vercel; o proxy de desenvolvimento não faz parte do build. URLs, e não segredos, podem estar no frontend. A configuração de publicação será entregue na etapa 5.

## Organização

- `src/app/pages/home/`: apresentação, serviços, loja, atendimento e contato.
- `src/app/components/`: cabeçalho com menu responsivo, rodapé, ícones SVG e formulário de orçamento.
- `src/app/core/company.ts`: telefone, WhatsApp, e-mail, endereço e link do mapa.
- `src/app/pages/admin/`: login e painel responsivo, carregados sob demanda.
- `src/app/core/auth.service.ts`: sessão em memória, proteção das rotas e autorização HTTP.
- `src/styles.css`: paleta, tipografia, espaçamentos, botões e regras de acessibilidade compartilhadas.
- `src/assets/img/`: imagens originais e versões otimizadas usadas na página.

Não há página nem dados de licitações. Caminhos desconhecidos redirecionam para a página inicial.

## Design e acessibilidade

O fundo azul profundo combina a rede de partículas conectadas do projeto original com grades técnicas e luzes estáticas discretas. Ciano e azul predominam; o vermelho da marca aparece em pequenos acentos. Cards, imagens e links têm microinterações, e os blocos entram progressivamente conforme aparecem na tela.

As imagens de computador e manutenção foram reduzidas para o tamanho de exibição; a imagem principal usa `srcset`, dimensões explícitas e prioridade alta, e as demais fotos carregam sob demanda.

A rede usa `particles.js` 2.0.0 com os mesmos parâmetros do projeto original: 80 partículas ciano, tamanho 3, conexões de 150 px com opacidade 0,2 e velocidade 2. Orbitron é carregada somente para os títulos; o restante usa fontes do sistema. AOS e o carrossel automático continuam removidos. As demais animações usam CSS e `IntersectionObserver`; com `prefers-reduced-motion`, a rede fica estática e menos densa. Há link para pular ao conteúdo, foco visível, rótulos persistentes, mensagens de validação associadas aos campos e menu operável por teclado (Escape fecha e devolve o foco).

O link do Google Maps pesquisa o endereço completo, sem coordenadas ou identificadores inventados. Verificado em 23/09/2026: o resultado corresponde a Rua Benedito Valadares, 78, São João Evangelista, MG, e lista a Telemicro no local.

## Formulário

O catálogo vem de GET `/api/v1/services`. O envio usa POST `/api/v1/budgets` com JSON (`customerName`, `phone`, `serviceCode`, `message`) e `Idempotency-Key`. A confirmação mostra o protocolo retornado pela API. Formspree foi removido desse fluxo.

- Nome, telefone com DDD e serviço são obrigatórios.
- Campos e botão são bloqueados durante o envio, com aviso acessível.
- Sucesso limpa os campos e mantém a confirmação visível.
- Erro ou espera acima de 90 segundos preserva os dados e oferece nova tentativa ou WhatsApp. O prazo acomoda a inicialização da hospedagem gratuita.
- Repetir o mesmo conteúdo após falha reutiliza a chave, evitando duplicação; mudar o conteúdo gera uma nova chave. A chave fica na memória desta página, e a API garante idempotência por 24 horas.
- A assinatura é cancelada se o componente for destruído.

Os testes usam HTTP simulado do Angular: não criam pedidos reais. Os pedidos ficam no PostgreSQL e aparecem no painel; não há envio de e-mail nesta etapa.

## Painel administrativo

Login em `/admin/login`, painel em `/admin`. O JWT fica somente na memória, sem localStorage, sessionStorage ou cookies. Recarregar a página exige novo login. Expiração ou HTTP 401 encerra a sessão; Sair remove o token local. O servidor valida as permissões em cada requisição.

O painel oferece busca, filtros por status/serviço e período, ordenação, paginação de 20 pedidos, contagens dos filtros, detalhes, histórico e observações. Datas do filtro usam o fuso local do navegador, incluindo o dia final inteiro. Contas ADMIN podem alterar status e adicionar observações; reabrir um pedido exige motivo. HTTP 409 atualiza os dados e preserva o texto para revisão. Após falha de conexão em uma alteração, atualize os detalhes antes de repetir a ação.

Contas DEMO têm somente leitura de dados explicitamente fictícios. A criação da conta DEMO e desses pedidos faz parte da etapa 5; novos pedidos públicos nunca aparecem nesse acesso.

## Verificação

O workflow `.github/workflows/checks.yml` executa testes e build do frontend e `verify` do backend em pushes e pull requests. Os testes do backend usam PostgreSQL isolado via Testcontainers. A execução automática começa após enviar esse arquivo ao GitHub; não acessa o banco da loja.

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

No Windows, se necessário, defina `CHROME_BIN` para o executável do Chrome.

Sete testes cobrem campos obrigatórios, nome em branco, telefone, conteúdo do POST, envio duplicado, sucesso, erro HTTP, falha de conexão, nova tentativa e timeout. A revisão no navegador incluiu larguras de 320, 390, 768 e 1440 pixels, ausência de overflow horizontal, navegação por âncoras, menu por teclado, foco de validação e destino do mapa.

Os metadados estão em `src/index.html` e o título da rota em `src/app/app.routes.ts`. A imagem de compartilhamento usa o domínio que já constava no projeto; se o domínio de produção mudar, atualize sua URL absoluta.
