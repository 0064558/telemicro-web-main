# Telemicro Informática

Landing page em Angular 20 standalone para a loja de informática em São João Evangelista, MG.

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

## Organização

- `src/app/pages/home/`: apresentação, serviços, loja, atendimento e contato.
- `src/app/components/`: cabeçalho com menu responsivo, rodapé, ícones SVG e formulário de orçamento.
- `src/app/core/company.ts`: telefone, WhatsApp, e-mail, endereço, link do mapa e endpoint do Formspree.
- `src/styles.css`: paleta, tipografia, espaçamentos, botões e regras de acessibilidade compartilhadas.
- `src/assets/img/`: imagens originais e versões otimizadas usadas na página.

Não há página nem dados de licitações. Caminhos desconhecidos redirecionam para a página inicial.

## Design e acessibilidade

O fundo azul profundo combina a rede de partículas conectadas do projeto original com grades técnicas e luzes estáticas discretas. Ciano e azul predominam; o vermelho da marca aparece em pequenos acentos. Cards, imagens e links têm microinterações, e os blocos entram progressivamente conforme aparecem na tela.

As imagens de computador e manutenção foram reduzidas para o tamanho de exibição; a imagem principal usa `srcset`, dimensões explícitas e prioridade alta, e as demais fotos carregam sob demanda.

A rede usa `particles.js` 2.0.0 com os mesmos parâmetros do projeto original: 80 partículas ciano, tamanho 3, conexões de 150 px com opacidade 0,2 e velocidade 2. Orbitron é carregada somente para os títulos; o restante usa fontes do sistema. AOS e o carrossel automático continuam removidos. As demais animações usam CSS e `IntersectionObserver`; com `prefers-reduced-motion`, a rede fica estática e menos densa. Há link para pular ao conteúdo, foco visível, rótulos persistentes, mensagens de validação associadas aos campos e menu operável por teclado (Escape fecha e devolve o foco).

O link do Google Maps pesquisa o endereço completo, sem coordenadas ou identificadores inventados. Verificado em 23/09/2026: o resultado corresponde a Rua Benedito Valadares, 78, São João Evangelista, MG, e lista a Telemicro no local.

## Formulário

O endpoint existente permanece `https://formspree.io/f/xeowyana`, recebendo `nome`, `telefone`, `servico`, `mensagem` e `_subject` por POST com FormData e resposta JSON.

- Nome, telefone com DDD e serviço são obrigatórios.
- Campos e botão são bloqueados durante o envio, com aviso acessível.
- Sucesso limpa os campos e mantém a confirmação visível.
- Erro ou espera acima de 20 segundos preserva os dados e oferece nova tentativa ou WhatsApp.
- A assinatura é cancelada se o componente for destruído.

Os testes usam o backend HTTP simulado do Angular: não enviam mensagens reais. A entrega de e-mail continua dependendo da configuração e disponibilidade da conta Formspree existente.

## Verificação

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

No Windows, se necessário, defina `CHROME_BIN` para o executável do Chrome.

Sete testes cobrem campos obrigatórios, nome em branco, telefone, conteúdo do POST, envio duplicado, sucesso, erro HTTP, falha de conexão, nova tentativa e timeout. A revisão no navegador incluiu larguras de 320, 390, 768 e 1440 pixels, ausência de overflow horizontal, navegação por âncoras, menu por teclado, foco de validação e destino do mapa.

Os metadados estão em `src/index.html` e o título da rota em `src/app/app.routes.ts`. A imagem de compartilhamento usa o domínio que já constava no projeto; se o domínio de produção mudar, atualize sua URL absoluta.
