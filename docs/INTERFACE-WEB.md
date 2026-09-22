# Interface web

Duas interfaces, servidas pelo próprio sistema a partir de
`src/main/resources/static/`. HTML, CSS e JavaScript puros nas duas: sem
framework, sem empacotador, sem npm, sem CDN, sem rede.

| Endereço | Interface | Decisão |
|---|---|---|
| `/` | **Conferência de enquadramento** — a porta de entrada | D012 (Etapa 11) |
| `/tecnica.html` | **Visão técnica** — apontamentos por regra e severidade | D010 (Etapa 9) |

As duas contam a mesma coisa com vocabulários diferentes, e por isso o rótulo
"visão técnica" é explícito: quem chegasse lá achando que é a tela de conferência
leria "achado" e "não avaliado" onde esperava "possível divergência" e "não foi
possível concluir".

## Como abrir

Ambas sobem com o perfil `api`:

    java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar servir

Depois, no navegador: <http://127.0.0.1:8080/>

**Duplo clique em `index.html` não funciona**, e não é defeito: o `file://`
bloqueia `import` entre módulos ES, e do outro lado não haveria API nenhuma.

---

# Parte 1 — Conferência de enquadramento (Etapa 11)

A pergunta que esta interface responde: *que tratamento a base normativa
carregada indica para os produtos desta nota, e o que o XML declara é coerente
com ele?* Decisão em `docs/DECISOES-ARQUITETURA.md`, **D012**.

## As telas

| Endereço | Tela |
|---|---|
| `#/` | Enviar a nota — uma ação, e só uma |
| `#/analise/<id>` | Resultado: a tela da nota, ou a do lote, conforme a contagem |
| `#/analise/<id>/produto/<endereco>` | Detalhe do produto — a tela em que a conferência acontece |
| `#/analise/<id>/grupo?ncm=&cClassTrib=&situacao=` | As notas e os itens de um grupo |
| `#/base?data=` | Base tributária carregada, numa data |
| `#/historico` | Análises anteriores, com volta para cada resultado |

**A tela inicial não abre com lista.** Quem chega quer conferir uma nota, e uma
lista de execuções no lugar do campo de envio obriga a pessoa a procurar o botão
antes de fazer a única coisa que veio fazer. O histórico está a um clique, abaixo.

**Uma nota abre a tela da nota; várias abrem a do lote.** Quem decide é a
contagem que o servidor mandou, e não a extensão do arquivo enviado: um `.zip`
com um documento só continua sendo uma nota.

## As quatro regras de apresentação, que são conteúdo

1. **"Não foi possível concluir" tem o mesmo peso visual dos outros três.** Nunca
   cinza, nunca atrás de um clique, nunca fora do resumo.
2. **O resumo mostra sempre os quatro números, inclusive os zeros.**
3. **Cor nunca é a única codificação**: todo estado sai com marca de forma
   (`▲ ◆ ■ ●`) e com o rótulo por extenso, os dois vindos do servidor.
4. **Em lugar nenhum um produto não avaliado é somado aos sem divergência.**

A quarta não depende de disciplina de quem escreve tela. No servidor,
`ContagemDeEstados` não oferece nenhum método que combine os dois estados. No
CSS, existe **uma única** regra `.estado-celula` para as quatro células, nenhum
seletor que reduza peso ou esconda zero, e nenhuma classe de "total",
"consolidado" ou "resumo somado" — não há onde escrever a soma.

**Nenhuma das quatro marcas é um visto de certo.** Um "✓" ao lado de "sem
divergência identificada" seria lido como *conferido*, que é a afirmação que o
sistema não faz. As marcas são geométricas e não carregam juízo.

## O terceiro número

O resumo traz três coisas, e não duas: os produtos por situação, as verificações
por estado, e **quantos produtos têm ao menos uma verificação sem conclusão**.

O terceiro existe porque a precedência esconde a pendência: um produto com uma
divergência e três verificações sem conclusão aparece como divergência — a mais
forte prevalece —, e sem essa linha a nota apareceria com "0 não foi possível
concluir".

## O detalhe do produto

Sete blocos, na ordem em que a pergunta se responde:

1. que produto é este, e a que situação as regras chegaram;
2. o que o documento declarou, campo a campo, com ausência escrita;
3. a descrição da nota ao lado da descrição que o catálogo dá ao NCM;
4. que tratamento a base normativa indica — **IBS e CBS em quadros separados**;
5. o declarado e o indicado lado a lado, **sem veredito próprio**;
6. por que cada regra chegou ao que chegou;
7. o aviso de uso.

**IBS e CBS são quadros próprios porque podem divergir**: um produto pode estar
coerente num e não no outro, e uma linha única esconderia o caso que interessa
conferir. Dentro do bloco do IBS, as parcelas estadual e municipal aparecem como
duas linhas, cada uma com a própria vigência e a própria fonte. **A tela não as
soma** — somar produziria um percentual que nenhuma linha da carga declara.

Os três tributos aparecem sempre, inclusive os que a carga não alcança, que vêm
com o motivo. Tributo omitido é lido como tributo que não incide.

### A comparação não emite um segundo veredito

O quadro de declarado e indicado põe os dois lados um do lado do outro e para aí.
Não escreve "confere" nem "não confere".

Quem julga são as sete regras, e o julgamento delas já está na situação do
produto. Um veredito ali seria um oitavo juízo, mais fraco: ignoraria a tolerância
de valor, a cobertura declarada da carga e as condições que cada regra examina —
e diria "diferente" onde a regra concluiu sem violação.

### "Por que este resultado" sai do que foi gravado

Não há tabela dizendo "R03 significa isto". Cada passo vem de uma de três
procedências, e a tela desenha as três diferente:

- **apontamento** — as evidências gravadas: campo examinado, valor encontrado,
  valor oposto, e de onde o valor veio;
- **pendência** — o motivo que a própria regra escreveu ao desistir;
- **conforme** — a conta que o derivou, dita como conta.

> **Revisão de 12/09/2026.** O cabeçalho de cada passo deixou de escrever só o
> código ("R06") e passou a escrever **o nome da regra, com o código ao lado**.
> O nome vem do servidor, em `regraNome`, e diz *que pergunta* a regra faz; a
> explicação de *por que* ela chegou ao resultado continua saindo só do que foi
> gravado. A frase acima continua verdadeira para a explicação. Ver
> "A regra sai pelo nome", na Parte 2.

## A tela do lote é outra tela

Não é a da nota repetida n vezes. Quem trabalha no fiscal corrige **cadastro**:
um NCM classificado errado aparece em quatrocentas notas e continua sendo um erro
de parametrização.

O agrupamento é por **(NCM + cClassTrib + situação)**, com o nível escrito — a
mesma disciplina da D010, agora com dado melhor, porque NCM e `cClassTrib` vêm do
que o documento declarou e não das evidências.

**A ordem padrão mede exposição, não gravidade.** Valor dos produtos envolvidos,
decrescente: um grupo caro com erro trivial de preenchimento sobe acima de um
grupo barato que perdeu um benefício. O significado da ordem fica visível na
tela, e há ordenação alternativa por contagem, para a pergunta oposta.

**O rótulo do valor nunca é abreviado.** "Valor dos produtos envolvidos" é o
tamanho da operação, não o do erro; abreviado para "valor", ao lado de "possível
divergência", seria lido como prejuízo. O rótulo vem do servidor.

## A faixa de procedência e o aviso de uso

Os dois vêm do servidor e são **exigidos no construtor** de toda resposta de
resultado. Se morassem no JavaScript, a tela nova que esquecesse não quebraria
nada — ficaria só sem aviso, que é o modo de falha mais provável e o menos
visível.

A faixa aparece em **toda** tela de resultado, e não só onde o catálogo é
exibido: a situação de um produto foi produzida contra aquela carga, e as telas
que as pessoas mais olham são justamente as que não mostram uma linha da tabela.
No caso parcialmente fictício ela **lista quais tabelas** são de demonstração.

## A base tributária abre pedindo a data

Sem valor padrão. É o caso de uso que a D003 previu — "o que vale hoje" como caso
de uso próprio, com data explícita — e um padrão silencioso de "hoje" faria a
resposta mudar sozinha de um dia para o outro, com a impressão da tela não dizendo
a que dia se refere. O botão "hoje" existe e não contradiz isso: é a pessoa
escolhendo hoje, com um clique que ela deu.

Ela **consulta** por NCM e por `cClassTrib`; não lista as tabelas inteiras. Os
repositórios do domínio expõem busca pontual, e alargá-los sairia da restrição
desta etapa — o que também é o formato útil, porque a tabela de NCM tem milhares
de linhas.

## Execução feita pela CLI não tem produtos a listar

O comando `auditar` grava apontamentos, mas não grava o acervo do que leu — ele
só passou a existir com a análise por esta tela. Abrir uma execução dessas mostra
o que aconteceu e oferece a visão técnica, em vez de exibir "0 produtos" ao lado
de "300 itens lidos".

## Verificação

**Não há teste automatizado de navegador**, pela mesma razão da Etapa 9: não há
dependência de navegador no `pom.xml`. A verificação foi por sonda em Node contra
um DOM mínimo, com dado fictício, nas duas direções — as telas desenham, as quatro
regras aparecem no texto produzido, e sabotá-las derruba a sonda. A sonda não é
versionada.

Registro honesto: **a primeira rodada da sonda deixou passar duas sabotagens.**
Abreviar o rótulo do valor passou porque a checagem olhava o texto da tela inteira
em vez do nó, e a frase também aparecia no botão de ordenação; trocar o selo por
"Conferido" passou porque o cenário não tinha nenhum produto sem divergência,
então aquele selo nunca era desenhado. Os dois furos foram corrigidos e as mesmas
sabotagens então caíram.

## Arquivos

    src/main/resources/static/
      index.html                        casca e navegação da conferência
      css/conferencia.css               os quatro estados e as peças desta interface
      js/conferencia/app.js             ponto de entrada e despacho de rota
      js/conferencia/roteador.js        roteamento por fragmento
      js/conferencia/api.js             a porta de escrita e as leituras da conferência
      js/conferencia/pecas.js           as quatro regras de apresentação, num lugar só
      js/conferencia/telas/*.js         uma tela por arquivo

`css/base.css`, `css/componentes.css` e os módulos de `js/` são da Etapa 9 e
**não foram alterados**. A paleta dos três desfechos é reaproveitada de propósito:
as figuras do TCC e a tela precisam usar a mesma cor para o mesmo estado.

> **Revisão de 12/09/2026.** A frase acima valeu até esta data. Para a regra sair
> pelo nome, `css/componentes.css` e os módulos `formato.js`, `svg.js`,
> `agrupamento.js`, `telas/panorama.js`, `telas/achados.js`, `telas/achado.js` e
> `telas/naoavaliados.js` foram alterados, com autorização. `css/base.css`,
> `dom.js`, `api.js`, `roteador.js`, `decimal.js`, `comum.js` e
> `telas/acuracia.js` continuam como a Etapa 9 os deixou.

---

# Parte 2 — Visão técnica (Etapa 9)

> **Emenda da Etapa 11.** Estas seis telas deixaram de ser a porta de entrada e
> passaram a viver em `tecnica.html`, sob o rótulo "visão técnica". Elas
> continuam inteiras, com os módulos JavaScript **sem uma linha alterada**, e
> estão a um clique da conferência — no menu e no fim da tela de análises
> anteriores. A tela de acurácia ficou no menu principal da conferência, porque é
> o resultado do TCC. Tudo o que esta parte registra continua valendo; o que
> mudou foi o endereço e o rótulo.
>
> **Revisão de 12/09/2026.** "Sem uma linha alterada" deixou de valer: as telas
> que escreviam o código da regra passaram a escrever o nome dela, com o código
> ao lado. Ver "A regra sai pelo nome", abaixo.

Cinco telas em HTML, CSS e JavaScript puros, servidas pelo próprio sistema a
partir de `src/main/resources/static/`. Sem framework, sem empacotador, sem npm,
sem CDN, sem rede. A decisão está em `docs/DECISOES-ARQUITETURA.md`, D010.

## Como abrir

A interface é servida pela mesma API de leitura da Etapa 8, então ela sobe com o
perfil `api`:

    java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar servir

Depois, no navegador: <http://127.0.0.1:8080/tecnica.html>

**Duplo clique em `tecnica.html` não funciona**, pelo mesmo motivo do topo
deste documento. A página que se abre por duplo clique é outra —
`visualizacao/resultados.html`, que gera as figuras do TCC a partir dos arquivos
exportados.

## O que ela faz, e o que não faz

Ela **lê**. Consome os quatro GET da Etapa 8 e nada mais. Não existe nesta
interface nenhum botão que audite, importe catálogo ou registre tratativa: os
três continuam na CLI, porque auditar tem efeito colateral gravado, importar
catálogo decide o que o sistema afirma sobre a norma, e tratar achado é ato de
uma pessoa identificada — e não há autenticação aqui.

## As telas

Os endereços abaixo são relativos a `tecnica.html`.

| Endereço | Tela |
|---|---|
| `#/` | Execuções, da mais recente para a mais antiga |
| `#/execucao/<id>` | Panorama: os três desfechos, por regra, motivos |
| `#/execucao/<id>/achados` | Achados, filtráveis e agrupados |
| `#/execucao/<id>/achado/<id>` | Detalhe: evidência campo a campo |
| `#/execucao/<id>/nao-avaliados` | O que o motor não julgou, linha a linha |
| `#/acuracia` | Acurácia, lida do CSV do `avaliar-acuracia` |

## Regras de apresentação que são conteúdo, não estilo

1. **`NAO_AVALIADO` é categoria própria em toda tela e todo gráfico.** Nunca
   somado a `CONFORME`, nunca omitido, nunca inferido por subtração. Uma execução
   com 4 apontamentos e 7 não avaliados não pode parecer lote quase limpo.
2. **"R04: 0 apontamentos" é distinguível de "R04: não avaliado"** em quatro
   lugares da mesma linha: a coluna de não avaliados traz o número inclusive em
   zero; o selo escreve "avaliou tudo" ou "nem tudo foi avaliado"; o gráfico
   desenha hachura própria para não avaliado e um traço curto para a regra que
   não apontou nem deixou pendência; e os motivos aparecem logo abaixo.
3. **Métrica indefinida é `(indefinida)`**, nunca 0, nunca 1, e sem barra
   desenhada — barra de altura zero diria "zero", e zero é um número.
4. **Cor nunca é a única codificação.** Todo desfecho tem símbolo, toda
   severidade tem triângulos, toda faixa de gráfico tem hachura e número escrito.
5. **Achados repetidos são agrupados por (NCM + cClassTrib + regra)**, com a
   contagem. Erro de parametrização é sistemático: corrige-se um cadastro, não
   oitocentas notas.
6. **A planilha é oferecida em toda tela de execução** — como comando pronto
   para copiar, não como link. Ver abaixo.

## A chave de agrupamento degrada, e diz que degradou

NCM e `cClassTrib` não são campos do achado: a API os expõe apenas dentro de
`evidencias`, e só quando a regra que apontou de fato os examinou.

| Regra | NCM na evidência | cClassTrib na evidência | Nível do grupo |
|---|---|---|---|
| cClassTrib declarado consta do catálogo (R01) | não | sim | `SOMENTE_CLASSTRIB` |
| CST compatível com o cClassTrib declarado (R02) | não | sim | `SOMENTE_CLASSTRIB` |
| Benefício declarado exige NCM vinculado a anexo (R03) | sim | sim | `NCM_E_CLASSTRIB` |
| NCM em anexo emitido com tributação integral (R04) | sim | sim | `NCM_E_CLASSTRIB` |
| Valor do tributo confere com base × alíquota do catálogo (R05) | não | não | `SOMENTE_REGRA` |
| NCM declarado consta do catálogo (R06) | sim | não | `SOMENTE_NCM` |
| Campos exigidos pelo cClassTrib vieram preenchidos (R07) | não | sim | `SOMENTE_CLASSTRIB` |

Cada grupo carrega o nível como campo (`nivel`) e como frase (`rotuloDoNivel`),
e o componente ausente vem `null` com o motivo escrito ao lado. **O motivo é
observado**: a página conta quantas evidências daquela regra, naquela execução,
trazem o campo — ela não tem tabela nenhuma dizendo o que cada regra examina.

Consequência honesta: para R05, a tela não responde "qual cadastro corrigir".
Ela diz isso, em vez de fingir uma chave que não tem.

## A regra sai pelo nome, com o código ao lado

*Revisão de 12/09/2026, nas duas interfaces.* Até esta data, toda tela escrevia a
regra só pelo código — "R06" —, que diz a quem programa qual classe rodou e não
diz nada a quem lê o resultado.

**O nome vem do servidor**, no campo `regraNome` de cada linha: achado, não
avaliado, linha por regra do panorama, verificação e passo da conferência. A
tabela mora em `infraestrutura/api/NomeDaRegra`, com as constantes `ID` das
próprias regras como chave. A página continua sem tabela nenhuma sobre as regras,
e o motivo da degradação da chave, acima, continua sendo observado e não
decorado. Cada nome resume a pergunta que a regra faz, tirada do Javadoc dela, e
não carrega valor normativo nenhum.

**O código não sumiu**: sai menor, ao lado do nome. É ele que a CLI aceita em
`--regra`, que a planilha escreve e que o gabarito de acurácia usa; sem ele na
tela não há como cruzar uma coisa com a outra. Os filtros mostram o nome e
continuam enviando o código no endereço.

**Regra sem nome é ausência escrita.** Uma execução gravada com um conjunto que o
código de hoje não monta pode trazer um código que a tabela não conhece. O nome
vem `null`, com `motivoDoNomeDaRegraAusente` ao lado, e a tela escreve o motivo
em itálico — nunca o código repetido no lugar do nome. `NomeDaRegraTest` compara a
tabela com o conjunto padrão nas duas direções, de modo que regra nova sem nome
quebra o build em vez de aparecer assim na tela.

**No gráfico por regra**, o nome não cabia à esquerda da barra e SVG não quebra
linha. Cada linha ganhou uma faixa de texto acima da barra, com o código e o nome.

**A tela de acurácia continua escrevendo só o código.** Ela lê o CSV do
`avaliar-acuracia` sem falar com a API (D008, D010), e o CSV não traz nome. Pôr o
nome ali exigiria mudar o escritor da Etapa 7 ou decorar a tabela no JavaScript,
e os dois ficaram fora desta revisão.

**Verificação**, pelo mesmo método das Etapas 9 e 11: sonda em Node contra um DOM
mínimo, com dado fictício, desenhando panorama, achados (agrupados e soltos),
detalhe do achado, não avaliados e detalhe do produto — 44 conferências
passando. Na direção contrária, cinco sabotagens, cada uma conferida como tendo
de fato alterado o arquivo: o rótulo ignorando o nome derruba nove conferências
em cinco telas; o gráfico ignorando o nome, uma; o texto corrido só com código,
três; o panorama voltando ao código, duas; o passo da conferência voltando ao
código, seis. A sonda não é versionada.

## A planilha não tem endpoint

A API tem quatro GET e nenhum deles produz arquivo. O botão **planilha (xlsx)**
abre a invocação do `exportar` com o identificador da execução já preenchido, e
um botão de copiar. Não é um link de download porque não há o que baixar — um
botão que parecesse baixar e devolvesse 404 seria pior.

## A tela de acurácia não consulta a API

Acurácia é propriedade de uma medição contra gabarito, não de uma execução de
auditoria, e o harness não persiste nada (D008). A tela lê o CSV do
`avaliar-acuracia` dentro do navegador, por campo de arquivo. Nenhum byte sai da
máquina.

**O que ela se recusa a ler:** o cabeçalho de comentários do CSV lista, em texto
claro, as chaves de acesso dos endereços que o motor não avaliou. Os dígitos
intermediários da chave carregam o CNPJ do emitente. A página conta essas linhas
e nunca as escreve.

## Arquivos

    src/main/resources/static/
      tecnica.html            casca e navegação da visão técnica
      css/base.css            tokens, tipografia, estados de ausência
      css/componentes.css     tabelas, chips, faixas, selos
      js/app.js               ponto de entrada e despacho de rota
      js/roteador.js          roteamento por fragmento
      js/api.js               os quatro GET e a busca de todas as páginas
      js/agrupamento.js       a chave que degrada e o nível escrito
      js/decimal.js           soma decimal em BigInt, sem ponto flutuante
      js/formato.js           data, número e ausência escrita
      js/svg.js               gráficos à mão, com hachura
      js/dom.js               construção de DOM por textContent
      js/comum.js             peças repetidas entre telas
      js/telas/*.js           uma tela por arquivo

Nada aqui é interpretado como marcação: todo texto vindo da API ou do CSV entra
por `textContent`, nunca por `innerHTML`.
