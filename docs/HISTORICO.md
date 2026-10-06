# Histórico do comportamento e migração de cargas antigas

O [README](../README.md) e os demais documentos de `docs/` descrevem só o
comportamento atual. Este arquivo guarda o que eles diziam antes, as emendas
datadas que o README acumulou, e o que fazer com cargas de catálogo e execuções
gravadas antes de cada mudança. As decisões completas, com o motivo de cada uma,
estão em [DECISOES-ARQUITETURA.md](DECISOES-ARQUITETURA.md); o registro etapa a
etapa está no [CLAUDE.md](../CLAUDE.md).

## Migração: o que fazer com cargas e execuções antigas

Toda mudança no formato do catálogo foi feita sem `INSERT` nem `UPDATE` em
migration: as colunas novas nascem nulas nas linhas antigas, e o sistema lê nulo
como "não declarado", nunca como um valor. **Reimportar a carga com os CSV
atualizados é o único caminho para as regras voltarem a concluir.**

| Carga ou execução gravada antes de… | Efeito ao reprocessar ou abrir | O que resolve |
|---|---|---|
| a coluna `natureza` (Etapa 11) | procedência não declarada; a tela pede reimportação, e a carga não é tratada como normativa | reimportar com `natureza` nos arquivos de dados |
| 30/09/2026 (`tributacaoIntegral`) | a coluna fica nula, e a R04 responde não avaliado para NCM em anexo | carga nova com a coluna |
| 01/10/2026 (`anexosAdmitidos` e `anexos-declarados.csv`) | a R03 responde não avaliado para todo código de benefício | carga nova com a coluna e o arquivo |
| 03/10/2026 (`camposObrigatoriosCondicionados` com três estados) | códigos sem nome de campo ficam "não declarado", e a R07 não conclui | carga nova com `NENHUM` ou com os nomes |
| 04/10/2026 (`natureza` no `cobertura.csv`) | a cobertura fica sem natureza declarada, e a carga deixa de ser dita normativa | reimportar com `natureza` no `cobertura.csv` |
| 04/10/2026 (falhas de leitura gravadas) | execução do `auditar` sai com a leitura "não registrada" na API, na planilha e na tela, em vez de zero | reprocessar o lote |
| 04/10/2026 (tolerância gravada) | execução sai com tolerância "não registrada", nunca com um valor | reprocessar o lote |

**CSV de catálogo escritos antes da coluna `natureza` param de importar até
ganharem a coluna.** O acréscimo é mecânico: `;natureza` no cabeçalho e
`;FICTICIO` (ou `;NORMATIVO`) em cada linha. Desde 04/10/2026 o mesmo vale para o
`cobertura.csv`.

A coluna é obrigatória de propósito: é ela que faz a tela avisar que está
exibindo dado de demonstração, sem depender de ninguém lembrar de ligar uma
configuração. Carga de origem desconhecida não é tratada como norma vigente:
supor isso seria a afirmação mais cara que o sistema poderia fazer por engano.

Os zeros que o resumo do histórico gravou para execuções do `auditar` antes de
04/10/2026 ficam no banco e não são lidos: a contagem dessas execuções sai "não
registrado".

## Versões das regras

| Regra | Versão atual | Desde | O que mudou |
|---|---|---|---|
| R01, R02, R06 | `1.0.0` | Etapa 3 | — (R01 e R06 mudaram só o texto da evidência em 04/10/2026, sem mudar de versão) |
| R04 | `1.1.0` | 30/09/2026 | "tributação integral" passou a ser declarada no catálogo (`tributacaoIntegral`), e não deduzida da redução |
| R03 | `1.1.0` | 30/09 a 02/10/2026 | confere o NCM contra os anexos admitidos de cada código, com cobertura por anexo |
| R07 | `1.1.0` | 03/10/2026 | só conclui quando o catálogo declarou os campos, inclusive `NENHUM`; lê o grupo de redução do XML |
| R05 | `1.3.0` | 03/10/2026 | `1.1.0` (30/09): aplica a redução do catálogo. `1.2.0` (03/10): código ausente com a tabela fora da cobertura é não avaliado. `1.3.0` (03/10): a redução de base sai da coluna `reducaoIncideSobre`, e não mais de CSTs escritos no código |

O conjunto passou de `2026.1` a `2026.6` ao longo dessas mudanças. Mudar a versão
de uma regra reabre os apontamentos tratados contra a versão anterior (ver
`tratar-achado` em [COMANDOS.md](COMANDOS.md#tratar-achado)).

O README antigo afirmava que "as sete regras, as severidades e os três desfechos
internos não mudaram — é o que mantém válidos os números de acurácia medidos na
Etapa 7". A primeira metade deixou de valer em 30/09/2026. A correspondência com
a acurácia da Etapa 7 foi conferida a cada mudança: nenhum rótulo do gabarito é
de R03, R04 ou R07, e os 6 rótulos da R05 dão as mesmas métricas em todas as
versões, medidas com o comparador real.

## Emendas que o README acumulou

Registradas aqui com o texto antigo resumido e a data em que deixou de valer.

### Uso geral

- **"Chamar sem argumento nenhum lista os comandos"** deixou de valer em
  21/09/2026 (`192c802`): sem argumento, a aplicação sobe o `servir` com o perfil
  `api`. A lista de comandos sai quando o nome do comando não é reconhecido.
- **"A aplicação exige a configuração obrigatória — sem ela, para na subida"**
  deixou de valer por partes: o sal se resolve sozinho desde a Etapa 10; usuário e
  senha do banco têm padrão desde 21/09/2026; a tolerância tem padrão declarado
  desde 04/10/2026. Sem banco acessível, a subida continua parando.
- **"Importar catálogo e tratar achado acontecem só pela linha de comando"**
  valeu até a Etapa 11. Desde a Etapa 12, com login e três perfis, os dois existem
  também pela web — importar catálogo só para administrador, tratar achado para
  fiscal e administrador. Pela linha de comando, `tratar-achado` passou a exigir
  usuário e senha.
- **Auditar** passou a ter um segundo caminho na Etapa 11: a tela de
  conferência, que envia a nota por `POST /api/analises` e dispara o mesmo
  pipeline.

### Banco

- **"Credenciais vêm do ambiente, nunca do repositório"** deixou de valer em
  21/09/2026 (`192c802`): sem as duas variáveis, o `application.properties` usa
  `auditoria` como usuário e como senha.

### Sal

- Até a Etapa 9, sem sal configurado a aplicação parava na subida. Desde a Etapa
  10 o sal se resolve em cascata, terminando num sal sorteado e gravado em arquivo
  local.

### Tolerância da R05

- Até 04/10/2026 o README dizia que a tolerância não tinha padrão e que a
  aplicação parava na subida sem ela. Deixou de ser verdade em 21/09/2026, quando
  o `application.properties` passou a trazer `0.01` dentro do placeholder da
  propriedade — e nada registrava qual valor tinha sido usado. Desde 04/10/2026 o
  padrão fica numa propriedade própria e toda execução grava o valor e a origem.

### Catálogo

- **14/09/2026** — datas passaram a aceitar `dd/mm/aaaa` ao lado de
  `aaaa-mm-dd`, em modo estrito; `classificacao-tributaria.csv` passou a aceitar a
  forma por tributo (`_cbs`/`_ibs`) para dispositivo legal, redução e fonte.
- **30/09/2026** — coluna opcional `tributacaoIntegral`, lida pela R04.
- **01/10/2026** — coluna opcional `anexosAdmitidos` e arquivo opcional
  `anexos-declarados.csv`, lidos pela R03. Até então, para a R03 bastava o NCM
  estar em qualquer anexo.
- **03/10/2026** — `camposObrigatoriosCondicionados` passou a ter três estados.
  Até essa data, a célula em branco virava "nenhum campo exigido", e a R07
  respondia conforme sem ter conferido nada. O vocabulário ganhou os seis campos
  do grupo de redução de alíquota do XML.
- **03/10/2026** — coluna opcional `reducaoIncideSobre`. Até essa data, a R05
  reconhecia a redução de base por dois CST escritos no código dela.
- **03/10/2026** — a R05 passou a ler a cobertura da tabela de classificações.
  Até essa data ela assumia "sem redução" para código ausente mesmo fora da
  cobertura, e chegava a apontar divergência em nota correta.
- **03/10/2026** — a guarda de cobertura passou a conferir também por anexo:
  anexo de NCM declarado carregado sem linha vigente em `item-anexo.csv` recusa a
  carga.
- **04/10/2026** — `cobertura.csv` passou a exigir `natureza`. Até então a fonte
  que ele declara, citada como fundamento dos apontamentos de R01 e R06, saía sob
  a faixa "Catálogo normativo" mesmo quando era fictícia.
- **04/10/2026** — dispositivo legal e fonte normativa sem nenhuma letra passaram
  a recusar a linha.
- **04/10/2026** — **UTF-8 estrito**, pela linha de comando e pela web, com a
  mesma mensagem. Até essa data a tela de cargas aceitava arquivo em
  Windows-1252 e gravava "DESCRI?O", enquanto a linha de comando o recusava sem
  dizer a codificação esperada. O gabarito segue a mesma regra.
- **04/10/2026** — **importação parcial.** "Cinco arquivos, todos obrigatórios"
  passou a valer só para a primeira carga. Na mesma data, a tela de cargas passou
  a aceitar o `anexos-declarados.csv`, que até então ela recusava como "não é de
  nenhuma tabela do catálogo".
- **04/10/2026** — elemento vazio em lista (`AAA||BBB`) passou a recusar a linha;
  antes era descartado em silêncio.
- **Etapa 12** — "Arquivo ausente não é tabela vazia; para declarar uma tabela
  sem registros, forneça o arquivo só com o cabeçalho" passou a valer só para
  `aliquota-vigente.csv`. As três tabelas com cobertura declarada não podem mais
  vir vazias. Na mesma etapa, a carga passou a ser recusada inteira com todas as
  linhas problemáticas listadas, em vez de parar na primeira.

### `auditar` e `exportar`

- **04/10/2026** — os arquivos que não puderam ser lidos passaram a ser gravados
  junto da execução. Até essa data o `auditar` só os imprimia, e a API, a
  planilha e a tela diziam que nenhum arquivo tinha falhado.
- **04/10/2026** — o mesmo documento repetido no lote passou a contar uma vez, e
  chave repetida com conteúdo diferente passou a deixar os dois arquivos de fora.
  Antes, o recibo contava 2 documentos e o banco guardava 1, e o item podia ser
  sobrescrito em silêncio.
- **04/10/2026** — a planilha passou a escrever `(sem tratativa)` em
  "Justificativa" e "Tratado em", que antes saíam em branco; o lado da tabela das
  evidências de R01 e R06 passou a dizer que a tabela não tem registro (antes
  saía `(não informado)`, sobre um código que a nota informou), inclusive nas
  execuções antigas; o valor em risco passou a sair na escala declarada.
- **Etapa 12** — a justificativa da tratativa na planilha passou a ter opt-in
  próprio (`auditoria.exportacao.expor-justificativa`). Até a Etapa 11 ela saía
  sempre.
- O README antigo dizia que a planilha tinha três abas; desde 04/10/2026 ela tem
  quatro, com "Não lidos".

### `servir` e API

- Até a Etapa 10 a API era **somente leitura**. Na Etapa 11 entrou uma porta de
  escrita, `POST /api/analises`. Na Etapa 12 entraram login, perfis, usuários,
  cargas, tratativas e correções.
- **"Não há autenticação"**, e por isso o bind em `127.0.0.1`, valeu até a Etapa
  11. Desde a Etapa 12 há autenticação, e o bind **continua** em `127.0.0.1`
  porque não há HTTPS.
- Os grupos de endpoints eram apresentados pela etapa em que entraram: leitura
  técnica (Etapa 8), conferência (Etapa 11), usuários e perfis (Etapa 12),
  histórico e acurácia (Etapa 13). A visão técnica, em `/tecnica.html`, é a
  interface da Etapa 9.
- **04/10/2026** — execução feita pela linha de comando passou a ter a contagem
  de produtos "não registrado" no histórico, em vez de zeros. A análise pela web
  tem contagem; a da linha de comando não, por escolha registrada na D020.

## Dados de exemplo

O README antigo dizia que "dados de exemplo existem apenas em
`src/test/resources` e são explicitamente fictícios". A frase valeu até
03/09/2026. Desde então existe também `exemplos/` — um catálogo, um gabarito e um
relatório de acurácia de demonstração —, **fora do Git**, porque é CSV (seção 8
do `CLAUDE.md`). Os dados de `src/test/resources` continuam fictícios.

Em 04/10/2026 o README registrava que cinco dos seis arquivos de
`exemplos/catalogo` declaravam a natureza `NORMATIVO` e só o `cobertura.csv`
declarava `FICTICIO`, e que o cabeçalho do `aliquota-vigente.csv` dizia que os
percentuais não eram referência normativa enquanto as linhas declaravam
`NORMATIVO`; a correção desse arquivo é do usuário, à mão. Na cópia local
conferida em 05/10/2026, os seis arquivos declaram `NORMATIVO`.

Sobre o catálogo de `exemplos/`, o README antigo registrava ainda: os valores de
`anexosAdmitidos` e de `anexos-declarados.csv` vêm dos arquivos de decisão
`dados/decisoes/d2-anexos-admitidos.csv` e `dados/decisoes/anexos-declarados.csv`,
não versionados; só o ANEXO-II e o ANEXO-III estão declarados carregados, e o IV,
o V, o VI e o IX têm linhas no `item-anexo.csv` mas são recortes; e, com aquele
catálogo, que não tinha sido alterado para a R07 `1.1.0`, a R07 dava não
avaliado em todos os códigos.
