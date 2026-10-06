# Formato dos arquivos CSV

Este documento descreve os arquivos CSV que o sistema lê: os seis do catálogo
normativo e o gabarito da medição de acurácia. Tudo aqui foi conferido contra o
código dos importadores (`infraestrutura/catalogo/`, `infraestrutura/csv/` e
`infraestrutura/acuracia/LeitorDeGabaritoCsv.java`).

Os exemplos são **fictícios de propósito** (`AAA`, `999`, `00000000`, datas em
1900). Nenhum deles é afirmação sobre a legislação: o sistema não traz conteúdo
normativo, e quem monta os CSV decide os valores a partir da norma.

## Regras comuns a todos os arquivos

- **Codificação UTF-8 estrita.** Arquivo em outra codificação — o caso comum é
  Windows-1252, o "ANSI" do Excel e do Bloco de Notas — é recusado com o nome do
  arquivo, a linha e o byte que não forma caractere. O sistema nunca troca o
  caractere por `?`.
- **Sem BOM.** O sistema não remove a marca de ordem de bytes: num arquivo
  "UTF-8 com BOM" a primeira coluna do cabeçalho não é reconhecida, e o arquivo é
  recusado por coluna ausente.
- Separador `;`. Campo entre aspas pode conter `;`, e `""` dentro das aspas vale
  uma aspa.
- Linhas em branco e linhas que começam com `#` são ignoradas. A primeira linha
  que sobra é o cabeçalho.
- As colunas são localizadas pelo nome, em qualquer ordem. Coluna a mais no
  cabeçalho é ignorada. Coluna sem nome ou repetida recusa o arquivo.
- Toda linha precisa ter o mesmo número de campos do cabeçalho.
- Datas em `aaaa-mm-dd` ou `dd/mm/aaaa`, em modo estrito: data inexistente, como
  `31/02/1900`, é recusada, nunca ajustada. Atenção: um arquivo em `mm/dd/aaaa`
  seria lido trocado quando o dia fosse até 12.
- Números aceitam vírgula ou ponto decimal, e mantêm as casas escritas.
- Listas dentro de uma célula usam `|`. Elemento vazio (`AAA||BBB`, `AAA|`,
  `|AAA`) recusa a linha; espaço em volta de cada elemento é tirado, exceto onde a
  seção do arquivo diz o contrário.
- Valores fixos (`FICTICIO`, `NORMATIVO`, `S`, `N`, `ALIQUOTA`, `BASE`, `NENHUM`,
  `NCM`, `NBS`, `NCM_E_NBS`, nomes de tributo e de tabela) são comparados com
  maiúsculas e minúsculas exatas. A exceção é `indicadorDeBeneficio`, que aceita
  `true`/`false` em qualquer caixa.

### Colunas presentes em todos os arquivos de dados do catálogo

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `vigenciaInicio` | obrigatória | obrigatória (exceto em `anexos-declarados.csv`) | data |
| `vigenciaFim` | obrigatória | em branco = vigência aberta | data, não anterior ao início |
| `fonteNormativa` | obrigatória | obrigatória | texto com ao menos uma letra |
| `natureza` | obrigatória | obrigatória | `FICTICIO` ou `NORMATIVO` |

- **`fonteNormativa` e `dispositivoLegal` sem nenhuma letra** — `0`, `-`, `123` —
  recusam a linha: um número ou sinal solto não identifica norma. O critério é só
  de forma; o sistema não confere se o texto aceito corresponde à norma.
- **`natureza`** diz se o conteúdo é transcrição de fonte normativa ou dado de
  demonstração, e é o que faz as telas, a API e a planilha avisarem quando o
  catálogo é fictício. Valor desconhecido recusa a linha, e duas naturezas no
  mesmo arquivo recusam o arquivo: um arquivo tem uma procedência só. A
  procedência é guardada por tabela, e uma carga mista aparece como "parcialmente
  fictícia", com a lista das tabelas de demonstração.
- **Vigências sobrepostas** do mesmo registro recusam a carga. "Mesmo registro" é
  o código em `classificacao-tributaria.csv`, o NCM em `registro-ncm.csv`, o par
  NCM + anexo em `item-anexo.csv` e o par tributo + abrangência em
  `aliquota-vigente.csv`.

### Como a carga é recusada

A carga é recusada **inteira** quando houver qualquer problema, e a mensagem
lista todos de uma vez — arquivo, linha, coluna e valor —, em vez de parar no
primeiro. Nada é gravado.

## Arquivos do catálogo

| Arquivo | Obrigatório | Conteúdo |
|---|---|---|
| `classificacao-tributaria.csv` | sim | o que o catálogo diz de cada cClassTrib |
| `registro-ncm.csv` | sim | os NCM existentes, com descrição |
| `item-anexo.csv` | sim | o vínculo entre NCM e anexo |
| `aliquota-vigente.csv` | sim | as alíquotas por tributo e abrangência |
| `cobertura.csv` | sim | o período e a fonte que a carga cobre em cada tabela |
| `anexos-declarados.csv` | não | a lista de anexos válidos e quais estão carregados |

Os nomes dos arquivos são exatos. **Na primeira carga os cinco obrigatórios
precisam vir.** Depois dela, uma importação pode trazer só parte deles: as
tabelas que faltam são copiadas da carga mais recente, que fica intacta, e o
resultado é uma carga nova. Ver `importar-catalogo` em
[COMANDOS.md](COMANDOS.md) e `POST /api/cargas` em [API.md](API.md).

**Arquivo ausente não é tabela vazia.** Só `aliquota-vigente.csv` pode vir
apenas com o cabeçalho. As três tabelas com cobertura declarada — classificação,
NCM e item de anexo — precisam ter ao menos um registro: dentro da cobertura,
registro ausente vira apontamento, e cobertura sobre tabela vazia faria todo item
do período ser apontado por silêncio do catálogo.

---

## `classificacao-tributaria.csv`

Uma linha por cClassTrib e vigência. É a tabela que R01, R02, R03, R04, R05 e R07
consultam.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `codigo` | obrigatória | obrigatória | o cClassTrib, sem espaço |
| `cstsCompativeis` | obrigatória | pode ficar em branco | CSTs separados por `\|`. Em branco, a R02 responde não avaliado |
| `dispositivoLegal` | obrigatória¹ | obrigatória | texto com ao menos uma letra |
| `indicadorDeBeneficio` | obrigatória | obrigatória | `true` ou `false` |
| `percentualReducao` | obrigatória¹ | pode ficar em branco | número. Em branco é "redução não declarada", que **não** é zero |
| `camposObrigatoriosCondicionados` | obrigatória | pode ficar em branco | `NENHUM`, ou nomes de campo separados por `\|` (lista abaixo) |
| `tributacaoIntegral` | opcional | pode ficar em branco | `S` ou `N` |
| `anexosAdmitidos` | opcional | pode ficar em branco | `NENHUM`, ou identificadores de anexo separados por `\|` |
| `reducaoIncideSobre` | opcional | pode ficar em branco | `ALIQUOTA` ou `BASE` |
| `vigenciaInicio`, `vigenciaFim`, `fonteNormativa`¹, `natureza` | ver regras comuns | | |

¹ Aceita também a **forma por tributo**, explicada abaixo.

**O que a célula em branco quer dizer** — em todas as colunas que aceitam
branco, branco é "o catálogo não declarou", e a regra que depende da coluna
responde não avaliado, com o motivo escrito:

- `camposObrigatoriosCondicionados` em branco: a R07 não conclui. `NENHUM`
  declara que o código não exige campo condicionado — é o único caminho para a
  R07 responder conforme sem lista de nomes.
- `tributacaoIntegral` ausente ou em branco: a R04 não conclui para NCM em anexo.
- `anexosAdmitidos` ausente ou em branco: a R03 não conclui para código de
  benefício. `NENHUM` declara que o benefício não depende de anexo.
- `reducaoIncideSobre` ausente ou em branco: a redução é lida como de alíquota.
  Com `BASE` e redução diferente de zero, a R05 responde não avaliado, porque não
  faz a conta de redução de base.
- `percentualReducao` em branco: a R05 não conclui para esse código.

**Nomes aceitos em `camposObrigatoriosCondicionados`** (o vocabulário do
sistema): `ncm`, `cfop`, `cstIbs`, `cstCbs`, `codigoClassificacaoTributaria`,
`baseCalculoIbs`, `baseCalculoCbs`, `aliquotaIbsUf`, `aliquotaIbsMunicipal`,
`aliquotaCbs`, `valorIbsUf`, `valorIbsMunicipal`, `valorCbs`,
`reducaoAliquotaIbsUf`, `aliquotaEfetivaIbsUf`, `reducaoAliquotaIbsMunicipal`,
`aliquotaEfetivaIbsMunicipal`, `reducaoAliquotaCbs` e `aliquotaEfetivaCbs`. Nome
fora desta lista não recusa a carga: a R07 responde não avaliado para o código e
diz qual nome não reconheceu.

**Forma por tributo.** `dispositivoLegal`, `percentualReducao` e
`fonteNormativa` podem vir, cada um, como par de colunas:
`dispositivoLegal_cbs` e `dispositivoLegal_ibs`, `reducao_cbs` e `reducao_ibs`,
`fonteNormativa_cbs` e `fonteNormativa_ibs`. A escolha é campo a campo.

- Textos iguais viram um só; textos diferentes são guardados como
  `CBS: … | IBS: …`.
- **Redução diferente recusa a linha**, inclusive o mesmo número escrito com
  casas decimais diferentes (`10` e `10,0`). O importador não escolhe entre os
  dois.
- Redução em branco nos dois lados é "não declarada"; em branco num lado só, a
  linha é recusada.

**Exemplo** (fictício):

```csv
codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;camposObrigatoriosCondicionados;tributacaoIntegral;anexosAdmitidos;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
AAA001;999;Dispositivo fictício AAA;false;0;NENHUM;S;;01/01/1900;;Fonte fictícia AAA;FICTICIO
AAA002;999|998;Dispositivo fictício BBB;true;99,99;cstIbs|cstCbs;N;ANEXO-AAA;01/01/1900;31/12/1900;Fonte fictícia BBB;FICTICIO
```

**Motivos de recusa da linha:**

- coluna obrigatória ausente do cabeçalho (recusa o arquivo);
- `codigo`, `dispositivoLegal`, `indicadorDeBeneficio`, `vigenciaInicio`,
  `fonteNormativa` ou `natureza` em branco;
- `codigo` com espaço, ou CST com espaço no meio; elemento vazio em
  `cstsCompativeis`;
- `indicadorDeBeneficio` diferente de `true`/`false`;
- redução que não é número;
- `dispositivoLegal` ou `fonteNormativa` sem nenhuma letra, em qualquer lado do
  par;
- o cabeçalho com a coluna única **e** o par por tributo para o mesmo campo, ou
  só metade do par (recusa o arquivo);
- `reducao_cbs` diferente de `reducao_ibs`;
- `tributacaoIntegral` diferente de `S`/`N`; `S` com `indicadorDeBeneficio`
  `true`; `S` com redução diferente de zero (`S` com redução em branco é aceito);
- `anexosAdmitidos` ou `camposObrigatoriosCondicionados` com `NENHUM` junto de
  outro valor, elemento vazio, elemento com espaço em volta ou elemento repetido;
- `reducaoIncideSobre` diferente de `ALIQUOTA`/`BASE`;
- data inválida, ou fim de vigência anterior ao início.

**Motivos de recusa da carga** ligados a este arquivo:

- código que cita em `anexosAdmitidos` um anexo que `anexos-declarados.csv` não
  declara;
- código que cita anexo quando `anexos-declarados.csv` não veio;
- vigências sobrepostas para o mesmo `codigo`;
- arquivo sem nenhum registro.

---

## `registro-ncm.csv`

Os NCM existentes. É a tabela da R06.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `ncm` | obrigatória | obrigatória | exatamente 8 dígitos |
| `descricao` | obrigatória | obrigatória | texto livre |
| `vigenciaInicio`, `vigenciaFim`, `fonteNormativa`, `natureza` | ver regras comuns | | |

**Exemplo** (fictício):

```csv
ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
00000000;DESCRICAO FICTICIA AAA;01/01/1900;;Fonte fictícia AAA;FICTICIO
00000001;DESCRICAO FICTICIA BBB;01/01/1900;31/12/1900;Fonte fictícia AAA;FICTICIO
```

**Motivos de recusa:** NCM sem 8 dígitos ou com caractere que não é dígito;
descrição em branco; vigências sobrepostas para o mesmo NCM; arquivo sem nenhum
registro; e as das regras comuns.

---

## `item-anexo.csv`

O vínculo entre NCM e anexo. É a tabela de R03 e R04. Um NCM pode estar em mais
de um anexo.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `ncm` | obrigatória | obrigatória | exatamente 8 dígitos |
| `identificadorDoAnexo` | obrigatória | obrigatória | texto, sem espaço em volta; é um rótulo livre |
| `tipoDeTratamento` | obrigatória | obrigatória | texto livre; aparece na evidência da R04 |
| `vigenciaInicio`, `vigenciaFim`, `fonteNormativa`, `natureza` | ver regras comuns | | |

Aqui a vigência é a do vínculo entre o NCM e o anexo, tirada da norma.

**Exemplo** (fictício):

```csv
ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
00000000;ANEXO-AAA;TRATAMENTO FICTICIO;01/01/1900;;Fonte fictícia AAA;FICTICIO
00000001;ANEXO-AAA;TRATAMENTO FICTICIO;01/01/1900;;Fonte fictícia AAA;FICTICIO
```

**Motivos de recusa:** NCM inválido; identificador ou tipo de tratamento em
branco; vigências sobrepostas para o mesmo par NCM + anexo; arquivo sem nenhum
registro; anexo declarado carregado em `anexos-declarados.csv`, de tipo `NCM` ou
`NCM_E_NBS`, sem nenhuma linha aqui vigente no período em que foi declarado
carregado; e as das regras comuns.

---

## `aliquota-vigente.csv`

As alíquotas por tributo. É a tabela da R05.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `tributo` | obrigatória | obrigatória | `IBS_UF`, `IBS_MUN` ou `CBS` |
| `percentual` | obrigatória | obrigatória | número, lido como porcentagem |
| `abrangencia` | obrigatória | obrigatória | texto, sem espaço em volta; é um rótulo livre |
| `vigenciaInicio`, `vigenciaFim`, `fonteNormativa`, `natureza` | ver regras comuns | | |

A R05 só faz a conta quando há **uma única** alíquota do tributo vigente na data
de emissão. Com mais de uma abrangência vigente para o mesmo tributo, ela
responde não avaliado e lista as abrangências. Este é o único arquivo que pode
vir só com o cabeçalho; aí a R05 responde não avaliado por falta de alíquota.

**Exemplo** (fictício):

```csv
tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
CBS;99,99;ABRANGENCIA-AAA;01/01/1900;;Fonte fictícia AAA;FICTICIO
IBS_UF;99,99;ABRANGENCIA-AAA;01/01/1900;;Fonte fictícia AAA;FICTICIO
```

**Motivos de recusa:** tributo fora dos três nomes; percentual em branco ou que
não é número; abrangência em branco ou com espaço em volta; vigências sobrepostas
para o mesmo par tributo + abrangência; e as das regras comuns.

---

## `cobertura.csv`

Declara, para cada uma das três tabelas com cobertura, o período que a carga
cobre e a fonte. É o que separa "o catálogo foi carregado para esta data e não
traz este registro" — que vira apontamento — de "esta tabela não foi carregada
para esta data" — que vira não avaliado. O sistema não deduz cobertura das linhas
importadas.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `tabela` | obrigatória | obrigatória | `CLASSIFICACAO_TRIBUTARIA`, `NCM` ou `ITEM_ANEXO` |
| `vigenciaInicio`, `vigenciaFim`, `fonteNormativa`, `natureza` | ver regras comuns | | |

Exatamente uma linha por tabela, as três obrigatórias. A fonte declarada aqui é
citada como fundamento dos apontamentos de R01 e R06, e por isso o arquivo também
declara `natureza`.

**Exemplo** (fictício; o arquivo real tem as três linhas):

```csv
tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
CLASSIFICACAO_TRIBUTARIA;01/01/1900;;Fonte fictícia AAA;FICTICIO
NCM;01/01/1900;;Fonte fictícia AAA;FICTICIO
```

**Motivos de recusa:** tabela desconhecida; tabela declarada duas vezes; alguma
das três tabelas sem linha; e as das regras comuns.

---

## `anexos-declarados.csv` (opcional)

Faz duas coisas:

1. **lista os anexos válidos.** Se um código de `classificacao-tributaria.csv`
   cita em `anexosAdmitidos` um anexo fora da lista, a carga é recusada. Também é
   recusada se algum código cita anexo e este arquivo não veio;
2. **diz quais anexos estão carregados por completo** em `item-anexo.csv`.

| Coluna | No cabeçalho | Célula | Valores aceitos |
|---|---|---|---|
| `identificadorDoAnexo` | obrigatória | obrigatória | o mesmo rótulo de `item-anexo.csv`, sem espaço em volta |
| `tipoDeCodigo` | obrigatória | obrigatória | `NCM`, `NBS` ou `NCM_E_NBS` |
| `vigenciaInicio` | obrigatória | pode ficar em branco | data |
| `vigenciaFim` | obrigatória | pode ficar em branco | data; exige `vigenciaInicio` |
| `fonteNormativa`, `natureza` | ver regras comuns | | |

**Aqui a vigência não é a da lei.** `vigenciaInicio` preenchida quer dizer "este
anexo está carregado por completo no `item-anexo.csv` a partir desta data".

- Anexo sem vigência existe, mas não está carregado. Para ele, a R03 responde não
  avaliado, com o motivo "anexo admitido não carregado", e não aponta, porque um
  recorte do anexo não prova que o NCM está fora dele.
- A R03 só aponta quando todos os anexos admitidos pelo código estão carregados
  na data e o NCM não está em nenhum deles.
- Anexo de `NBS` com vigência e sem nenhuma linha em `item-anexo.csv` está
  carregado e vazio de NCM: para ele, a R03 aponta qualquer NCM. Anexo de `NCM`
  ou `NCM_E_NBS` declarado carregado sem linha vigente é recusado (ver
  `item-anexo.csv`).

**Exemplo** (fictício):

```csv
identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
ANEXO-AAA;NCM;01/01/1900;;Fonte fictícia AAA;FICTICIO
ANEXO-BBB;NCM;;;Fonte fictícia AAA;FICTICIO
```

**Motivos de recusa:** identificador em branco, com espaço em volta ou repetido;
`tipoDeCodigo` fora dos três valores; fim de vigência sem início; e as das regras
comuns.

---

## Gabarito da acurácia

O gabarito do `avaliar-acuracia` e da tela de acurácia é rotulado à mão: uma
linha por item e regra que se queira medir. Segue as regras comuns de leitura
(UTF-8 estrito, `;`, `#` como comentário, colunas em qualquer ordem).

| Coluna | Célula | Valores aceitos |
|---|---|---|
| `chave_documento` | obrigatória | chave de acesso do documento, 44 dígitos |
| `numero_item` | obrigatória | número do item no documento, a partir de 1 |
| `regra_id` | obrigatória | identificador da regra, como `R05`. O leitor não recusa identificador desconhecido |
| `rotulo_esperado` | obrigatória | `ACHADO` ou `CONFORME` — nada mais |

**Exemplo** (fictício):

```csv
chave_documento;numero_item;regra_id;rotulo_esperado
00000000000000000000000000000000000000000000;1;R05;ACHADO
00000000000000000000000000000000000000000000;2;R05;CONFORME
```

`NAO_AVALIADO` não é rótulo de gabarito: quem rotula responde sobre o documento,
não sobre o sistema. Como o resultado é lido, ver `avaliar-acuracia` em
[COMANDOS.md](COMANDOS.md).

**Motivos de recusa:** coluna ausente; célula em branco; chave que não tem 44
dígitos; `numero_item` que não é inteiro ou é menor que 1; rótulo fora de
`ACHADO`/`CONFORME` (inclusive `NAO_AVALIADO`); arquivo fora de UTF-8.
