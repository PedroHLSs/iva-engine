# Decisões de Arquitetura

Registro leve de ADRs. Uma decisão por etapa do projeto.

Formato: **Contexto** (o que motivou), **Decisão** (o que foi escolhido),
**Consequência** (o que passa a valer, incluindo o que fica mais difícil).

Status possíveis: `Aceita`, `Substituída por Dxxx`, `Revogada`.

---

## D001 — Domínio isolado de framework

**Etapa:** 0 — Fundação do repositório
**Status:** Aceita

### Contexto

O núcleo do sistema é um conjunto de regras de coerência sobre campos de IBS e
CBS de documentos fiscais. Essas regras são o objeto de estudo do TCC: são elas
que precisam ser lidas, discutidas e testadas com facilidade, tanto por quem
avalia o trabalho quanto por quem o mantém depois.

Duas forças puxavam em direções opostas:

1. O caminho de menor esforço em Spring Boot é anotar tudo — entidades JPA que
   também são o modelo de negócio, componentes Spring em toda parte, objetos de
   domínio que carregam anotações de serialização Jackson/JAXB porque em algum
   momento precisaram virar XML ou JSON.
2. Esse caminho amarra a regra de negócio ao ciclo de vida do framework. Testar
   uma regra passa a exigir contexto Spring; entender a regra passa a exigir
   entender o mapeamento; e uma troca de detalhe técnico (ORM, biblioteca de
   XML, formato de entrada) vaza para dentro da regra.

O projeto tem ainda uma característica que agrava o item 2: o conteúdo
normativo não é conhecido em tempo de compilação — ele entra por importação de
CSV em tempo de execução. As regras de domínio precisam ser expressas sobre
dados que chegam de fora, sem depender de como chegam.

### Decisão

O código é dividido em três pacotes sob `br.edu.tcc.auditoria`, com dependência
em sentido único:

```
infraestrutura ──▶ aplicacao ──▶ dominio
```

- `dominio` — regras de coerência, modelo dos documentos e dos apontamentos.
  **Não importa nada de Spring, JPA, Jackson ou JAXB.** Usa apenas a biblioteca
  padrão do Java.
- `aplicacao` — casos de uso e orquestração. Quando precisa de um recurso
  externo, declara uma interface e depende dela, não da implementação.
- `infraestrutura` — leitura de XML, acesso a PostgreSQL, importação de CSV,
  configuração Spring, entrada e saída.

### Consequência

Ganhos:

- As regras de auditoria são testáveis com JUnit puro, sem subir contexto
  Spring — testes rápidos e legíveis, que é o que o TCC precisa demonstrar.
- O modelo de domínio pode ser desenhado a partir do problema fiscal, e não a
  partir do que o ORM ou o parser de XML tornam conveniente.
- Trocar a forma de ler o XML ou de persistir não toca em regra de negócio.

Custos aceitos:

- Haverá duplicação estrutural: entidade de persistência e objeto de domínio
  serão tipos distintos, com tradução explícita entre eles na infraestrutura.
  Isso é deliberado, não é omissão a ser "corrigida" depois.
- Não se pode usar o atalho de anotar o objeto de domínio para serializar. Todo
  mapeamento para XML/JSON acontece na infraestrutura, em tipos próprios.
- A regra exige vigilância: uma única anotação de framework no domínio anula a
  decisão. Está registrada em `CLAUDE.md` como restrição permanente.

---

## D002 — Ausência é um estado, não um zero

**Etapa:** 1 — Domínio puro
**Status:** Aceita

### Contexto

O sistema audita documentos já emitidos. A matéria-prima é o que o contribuinte
declarou — e, com igual importância, o que ele **não** declarou. Um item sem
base de cálculo de IBS e um item com base de cálculo declarada como zero são
situações fiscalmente distintas: a primeira é omissão de campo, a segunda é
informação prestada. Elas produzem apontamentos diferentes.

O caminho de menor esforço apaga essa diferença em três lugares distintos, e em
todos eles por conveniência técnica:

1. leitura de XML — campo ausente vira `null`, que vira `0` na primeira
   operação aritmética;
2. mapeamento de banco — coluna `NOT NULL DEFAULT 0`;
3. assinatura de método — `BigDecimal getBaseCalculoIbs()` não tem como
   devolver "não veio".

Se qualquer um dos três converter ausência em zero, o sistema passa a afirmar
que o contribuinte declarou algo que ele não declarou. Num TCC, isso não é um
bug de arredondamento: é o relatório mentindo sobre o documento.

Havia ainda uma segunda pressão, do mesmo tipo: o modelo precisa guardar quem
emitiu e quem recebeu o documento, mas o repositório processa notas reais de uma
empresa, e CNPJ, CPF, razão social e endereço não podem circular pelo núcleo do
sistema.

### Decisão

**Ausência tem uma grafia só.** Todo campo que pode não vir no documento é
`Optional<T>` no domínio. `null` e texto em branco são rejeitados na construção,
com exceção de domínio, e não normalizados para vazio: aceitar uma segunda
grafia de ausência traria de volta o problema por outra porta.

Decorrem daí quatro escolhas concretas:

- **Sem construtor abreviado e sem valor padrão.** Não há builder com defaults
  em `ItemDocumento` nem em `Documento`. Quem monta um item é obrigado pelo
  compilador a dizer, campo a campo, se o dado veio ou não. A verbosidade no
  ponto de construção é o preço de não haver omissão silenciosa.
- **`BigDecimal` em todo valor monetário, nunca `double`**, com a escala
  declarada preservada como veio. Para a auditoria, `0` e `0,00` são registros
  diferentes do mesmo número. Como `BigDecimal.equals` distingue escala,
  comparação de grandeza nas regras usa `compareTo`.
- **`ResultadoAvaliacao.NAO_AVALIADO` é um desfecho de primeira classe**, ao
  lado de `ACHADO` e `CONFORME`. Regra que não pôde ser aplicada — faltou campo,
  faltou tabela, data fora de vigência — não é regra que não encontrou nada.
- **`ValorEmRisco` é tipo selado**, com as variantes `Calculado` e
  `NaoCalculavel`. Um `Optional<BigDecimal>` solto permitiria vazio sem
  explicação; aqui `NaoCalculavel` exige o motivo no construtor, e é impossível
  registrar ausência de valor sem dizer por quê.

**Participantes só entram pseudonimizados.** `IdentificadorPseudonimizado`
aceita exclusivamente resumo criptográfico de 256 bits em hexadecimal minúsculo:
64 caracteres em `0-9a-f`. Nenhum CNPJ, CPF, razão social ou endereço satisfaz
esse formato, de modo que a tentativa de guardar o valor original falha na
construção. O cálculo do resumo fica na infraestrutura, única camada que vê o
dado real. O domínio não tem campo para o valor em texto claro e não sabe
reverter o pseudônimo — para auditar coerência de IBS/CBS basta saber se dois
documentos têm o mesmo participante, e a igualdade do pseudônimo responde isso.

**Validação estrutural, nunca normativa.** Os objetos de valor validam apenas a
forma, e apenas quando a forma é sabidamente independente da legislação:
`ChaveAcesso` 44 dígitos, `Ncm` 8, `Cfop` 4. `CodigoCst` e
`CodigoClassificacaoTributaria` **não** validam conjunto de códigos nem
comprimento: essa tabela é conteúdo normativo, chega por importação de CSV e é
confrontada pelas regras, não pelo construtor. `Uf` é enum fechado das 27
unidades federativas — divisão político-administrativa, não matéria tributária,
e sem nenhuma alíquota, código ou vigência associada. Nenhuma data está fixada
em código: `PeriodoVigencia` recebe as suas das tabelas importadas.

Pela mesma razão, campos numéricos não são validados contra faixa ou sinal. O
sistema precisa conseguir representar inclusive o que está errado, sob pena de
não ter o que apontar.

**Documento e item são tipos separados.** `Documento` não carrega lista de
itens; uma regra que precise dos dois recebe o par, e compô-los é tarefa da
aplicação.

**A regra D001 passa a ser verificada por teste.** `DominioNaoDependeDeFramework`
lê os fontes de `dominio/` e falha se algum importar qualquer coisa fora de
`java.*` e do próprio domínio.

### Consequência

Ganhos:

- A distinção entre omissão e declaração sobrevive da leitura ao relatório, e
  está coberta por teste dedicado (`CampoAusenteNaoEZero`).
- Um apontamento sem evidência, sem fundamento normativo, sem versão de regra ou
  com valor ausente e inexplicado não compila nem constrói.
- Dado pessoal em texto claro não tem por onde entrar no núcleo.
- A proibição de framework no domínio deixou de depender de vigilância humana.

Custos aceitos:

- Construir um `ItemDocumento` exige quinze argumentos explícitos, quase todos
  `Optional`. É verboso de propósito e não deve ser "resolvido" com builder de
  defaults.
- A infraestrutura fica com trabalho que o domínio se recusa a fazer: aparar
  espaços, normalizar maiúsculas, calcular o resumo criptográfico, e decidir o
  que fazer com documento cuja chave ou cujo NCM não têm sequer a forma
  esperada.
- Nenhuma regra de auditoria pode ser escrita antes de as tabelas normativas
  existirem. Isso é intencional: sem tabela, a saída correta é `NAO_AVALIADO`.

---

## D003 — Toda consulta normativa é resolvida na data do documento

**Etapa:** 2 — Catálogo normativo e resolução por vigência
**Status:** Aceita

> Numeração: esta é a decisão que a Etapa 2 pediu sob o rótulo "D002". Como
> `D002` já estava ocupado pela Etapa 1, foi registrada como `D003`.

### Contexto

O sistema audita documentos já emitidos, e o conhecimento jurídico que ele
consulta muda ao longo do tempo. Um `cClassTrib` pode ter um conjunto de CSTs
compatíveis num período e outro no período seguinte; um NCM pode entrar e sair
de um anexo; uma alíquota é substituída. Auditar um documento emitido no ano
passado contra a versão de hoje do catálogo produz apontamento fundamentado em
norma que não valia quando o documento foi emitido.

Esse erro tem uma característica que o torna especialmente perigoso num TCC:
**ele é silencioso e o resultado parece plausível**. O relatório sai bem
formatado, com fundamento citado e valor calculado, e está errado. Nada falha,
nada avisa.

O caminho de menor esforço conduz direto a ele, por três portas:

1. o repositório expõe `buscarPorCodigo(codigo)` e alguém, mais tarde, precisa
   de "o registro atual";
2. uma regra chama `LocalDate.now()` porque a data não chegou até ela;
3. o catálogo guarda só a última versão de cada registro, porque foi assim que
   o CSV foi carregado.

Havia ainda um segundo problema, independente do primeiro: **catálogo com duas
versões do mesmo registro valendo na mesma data**. Quem consulta não tem como
saber qual das duas responder, e qualquer critério de desempate — a mais
recente, a primeira carregada, a de maior vigência — seria inventado por quem
escreveu o código, não pela norma.

### Decisão

**A data é fixada uma vez, na fronteira, e as regras não a veem.**

- `ContextoNormativo` é a única porta pela qual uma regra de auditoria consulta
  o catálogo. **Nenhum método dela aceita data como parâmetro**, e nenhum
  devolve data. A regra pergunta "o que o catálogo diz sobre este código?" e
  recebe a resposta já resolvida.
- `ContextoNormativoNaData` recebe a data de emissão do documento no construtor
  e a guarda em campo privado sem acessor. Não há construtor sem data.
- Os repositórios — `RepositorioClassificacaoTributaria`, `RepositorioNcm`,
  `RepositorioItemAnexo`, `RepositorioAliquota` — recebem `LocalDate` como
  parâmetro, porque é neles que a resolução acontece. Regra nenhuma fala com
  eles.
- Não há chamada a `LocalDate.now()` em nenhum ponto do domínio.
- Uma regra que precise registrar a vigência aplicada num `Achado` a toma do
  registro que consultou, via `RegistroNormativo.vigencia()` — a vigência que
  de fato sustentou o apontamento, e não uma data solta que a regra teria de
  correlacionar por conta própria.

**Vigência e fonte moram num tipo só.** `ProcedenciaNormativa` reúne
`vigenciaInicio`, `vigenciaFim` e `fonteNormativa`, e todo registro do catálogo
a carrega através da interface `RegistroNormativo`. Não é economia de digitação:
é a garantia de que um tipo novo de registro não possa nascer sem data ou sem
fonte. Registro sem vigência não pode ser resolvido no tempo; registro sem fonte
gera apontamento que ninguém consegue conferir.

**Sobreposição de vigência é erro de catálogo, e falha na carga.**
`SerieNormativa` agrupa as versões de uma mesma chave e se recusa a existir se
duas delas valerem na mesma data. A invariante fica no domínio, não no
repositório: trocar armazenamento em memória por PostgreSQL não pode ser
oportunidade de perdê-la. A carga falha inteira, e não parcialmente — catálogo
meio carregado responderia vazio para o que faltou, e vazio significa "o
catálogo nada diz", de modo que um erro de carga sairia no relatório disfarçado
de `NAO_AVALIADO`.

**A chave da série é a identidade completa do registro, não um campo isolado.**
Item de anexo tem chave `NCM + anexo`; alíquota tem chave
`tributo + abrangência`. Se a chave do item de anexo fosse só o NCM, o catálogo
recusaria como "sobreposição" um NCM vinculado a dois anexos ao mesmo tempo — o
que equivaleria a o código afirmar que isso não pode acontecer. Quem afirma o
que pode e o que não pode é a fonte importada.

**Consulta sem resposta devolve vazio, nunca a versão mais próxima.** Data
anterior à primeira vigência, posterior à última, ou caída num intervalo
descoberto entre duas versões: todas devolvem vazio. Vazio significa "o catálogo
nada diz nesta data" e leva a `NAO_AVALIADO` — nunca a conformidade, e nunca a
um chute pela versão vizinha.

**O catálogo entra vazio e só por importação.** Os quatro importadores CSV
recusam qualquer linha sem `vigenciaInicio` ou sem `fonteNormativa`, sempre
indicando o número da linha física do arquivo — quem opera o catálogo não é quem
escreveu o código, e "linha 7 sem fonteNormativa" é acionável enquanto "erro de
importação" não é. Quando um valor lido viola invariante do domínio, a exceção
do domínio vai como causa e a mensagem acrescenta a linha.

### Consequência

Ganhos:

- É estruturalmente impossível uma regra de auditoria consultar o catálogo numa
  data que não seja a do documento. Não depende de disciplina de quem escreve a
  regra, e há teste por reflexão que falha se algum método de
  `ContextoNormativo` passar a aceitar ou devolver data.
- O mesmo documento auditado hoje e daqui a dois anos produz o mesmo relatório,
  desde que o catálogo não seja reescrito retroativamente.
- Todo apontamento consegue citar a fonte e a vigência exatas que o
  sustentaram, porque o registro consultado as carrega.
- Catálogo contraditório falha alto, na carga, e não silenciosamente na
  consulta.

Custos aceitos:

- A camada de aplicação passa a ter uma responsabilidade obrigatória: construir
  um `ContextoNormativo` por documento auditado. Não há contexto global nem
  reaproveitável entre documentos de datas diferentes.
- Consultar "o que vale hoje" não é uma operação que o sistema ofereça a regras.
  Se algum dia for preciso — para uma tela de conferência de catálogo, por
  exemplo — isso será um caso de uso próprio na aplicação, com data explícita,
  e não um método novo em `ContextoNormativo`.
- Corrigir uma linha errada do catálogo exige entender vigências: não se
  sobrescreve o registro, fecha-se a vigência anterior e abre-se a seguinte.
  Cargas descuidadas falham em vez de "funcionar".
- O leitor de CSV é próprio, sem biblioteca. Suporta aspas e separador `;`, mas
  não campo com quebra de linha — o que manteria a numeração de linha honesta
  deixaria de valer, e a numeração é o que torna a recusa acionável.

---

## D004 — Silêncio do catálogo só vira apontamento dentro da cobertura declarada

**Etapa:** 3 — Motor de regras
**Status:** Aceita

### Contexto

As regras de auditoria perguntam ao catálogo e reportam o que ouvem. A regra de
ouro da etapa é clara: **se o catálogo não tiver dado suficiente para julgar o
item, o resultado é `NAO_AVALIADO` com motivo, jamais `CONFORME`.** Ausência de
dado e conformidade não podem se parecer na saída.

Ao escrever as sete regras, apareceu um caso em que essa regra, aplicada ao pé
da letra, se autodestrói. R01 pergunta se o `cClassTrib` declarado consta do
catálogo, e R06 pergunta o mesmo do NCM. Nessas duas, "o catálogo nada diz" é
exatamente o achado que se quer produzir. Se elas devolvessem `NAO_AVALIADO`
sempre que o código não fosse encontrado, jamais apontariam coisa alguma — e o
sistema perderia a verificação mais elementar que tem.

O inverso é igualmente ruim. `ContextoNormativo.classificacaoTributaria(codigo)`
devolve vazio tanto quando a tabela foi carregada e o código não consta dela
quanto quando a tabela não foi carregada. Um catálogo vazio faria R01 e R06
acusarem todo item de todo documento, com aparência de conclusão firme. Numa
ferramenta de auditoria, apontamento falso é pior que apontamento faltante: o
primeiro acusa, o segundo se mostra.

Havia ainda um problema menor de mesma origem. `Achado` exige
`fundamentoNormativo` e `vigenciaAplicada`, e D003 manda tomá-los do registro
consultado. Em R01 e R06 não há registro consultado — a inexistência dele é o
achado.

O mesmo dilema aparece, em outra forma, em R03 e R04, que leem o vínculo entre
NCM e anexo: tabela de anexos não carregada faria R03 apontar todo benefício
como sem respaldo e faria R04 dar todo item por conforme.

### Decisão

**Quem carrega o catálogo declara, por tabela, a cobertura daquela carga** —
vigência e fonte, reunidas na `ProcedenciaNormativa` que o resto do sistema já
usa, agrupadas em `CoberturaDoCatalogo` para as três tabelas que precisam dela.

- **Dentro da cobertura**, silêncio do catálogo é resposta: o código não consta,
  e isso vira apontamento.
- **Fora da cobertura**, silêncio é falta de dado: `NAO_AVALIADO`, com motivo
  dizendo qual período a carga cobre e qual é a data de emissão.

Os valores não são escritos em código: chegam de fora, junto dos CSVs a que se
referem, como todo o resto do conteúdo normativo. A cobertura é também o que dá
fundamento e vigência ao apontamento de "não consta", já que não há registro de
onde tomá-los.

Decorrem daí as demais escolhas da etapa:

- **`Avaliacao` é tipo selado em três variantes**, uma por `ResultadoAvaliacao`.
  `Conforme` não tem campo de texto; `NaoAvaliada` exige motivo não vazio;
  `ComAchado` exige o `Achado`. É impossível construir uma avaliação que diga
  "não avaliei" sem dizer por quê, e as três se distinguem pelo tipo, sem
  inspeção de texto.
- **Toda avaliação se identifica** — regra, versão, documento e item. Um
  `NAO_AVALIADO` solto não diria a que item se refere, e relatório de auditoria
  não tem linha sem endereço.
- **`NAO_AVALIADO` continua na saída do motor.** Nada é filtrado. Devolver só os
  achados faria o relatório perder a contagem de regras que não puderam ser
  aplicadas, que é o que distingue auditoria honesta de auditoria que parece
  limpa.
- **Ordem determinística por construção**: chave de acesso, número do item,
  posição da regra no conjunto. A ordenação por chave torna a saída independente
  da ordem de entrada, que costuma vir de listagem de diretório ou de consulta a
  banco e não promete estabilidade. Onde uma regra lê coleção sem ordem
  garantida — o conjunto de CSTs compatíveis, a lista de anexos de um NCM — ela
  ordena antes de montar a evidência.
- **O motor recebe um `ProvedorDeContextoNormativo`, não um contexto.**
  Documentos de datas diferentes não podem compartilhar catálogo resolvido, sob
  pena de reintroduzir o erro que D003 fecha. A variante de documento único
  recebe o contexto direto, porque ali só há uma data.
- **`ConjuntoRegras` tem versão própria** (`2026.1`), separada da versão de cada
  regra. Acrescentar, remover ou reordenar regras muda o relatório sem mudar
  versão de regra nenhuma. Como a obrigação de subir a versão não tem como ser
  garantida pelo compilador, há teste que fixa o inventário do conjunto padrão:
  mexeu em regra, o teste falha, e a decisão de versionamento é tomada na hora.
- **R07 precisa de vocabulário publicado.** O catálogo cita campos exigidos por
  nome, em texto. `CampoDoItem` é esse vocabulário, e usa exatamente os nomes dos
  componentes de `ItemDocumento`, com teste por reflexão que falha se algum for
  renomeado. Nome que o vocabulário não reconhece não é adivinhado: vira
  `NAO_AVALIADO` citando o nome, ou evidência dentro do achado quando já houver
  campo conhecido faltando.

### Consequência

Ganhos:

- R01 e R06 apontam de verdade, e param de apontar quando a carga não cobre a
  data — sem que a diferença dependa de disciplina de quem escreve a regra.
- Todo apontamento continua citando fonte e vigência, inclusive os que nascem da
  ausência de registro.
- Regra nova acrescentada ao conjunto passa a ser cobrada automaticamente pelo
  teste transversal da regra de ouro, sem depender de alguém lembrar de escrever
  o teste específico.
- Dois relatórios do mesmo lote podem ser comparados linha a linha.

Custos aceitos:

- Quem opera o catálogo tem uma obrigação a mais: declarar a cobertura de cada
  carga. Declarar cobertura para uma tabela que não foi carregada faz o sistema
  apontar; é erro de operação, e é visível.
- **R03 verifica vínculo do NCM a _algum_ anexo, não ao anexo correspondente ao
  `cClassTrib`.** O catálogo da Etapa 2 não guarda o vínculo
  `cClassTrib → anexo`. A verificação implementada é condição necessária da
  pretendida, e deixa passar o caso do NCM que consta de anexo diferente do que
  o código invocado pressupõe. Fechar isso exige acrescentar o vínculo ao
  catálogo — mudança em etapa entregue, não feita aqui.
- **R05 não escolhe abrangência.** Quando o catálogo traz mais de uma alíquota
  vigente para o mesmo tributo na data, a regra devolve `NAO_AVALIADO` nomeando
  as abrangências. Escolher uma seria afirmar qual se aplica a qual operação, o
  que é conteúdo normativo.
- **R05 lê o percentual do catálogo como porcentagem**, isto é, o valor esperado
  é `base × percentual ÷ 100`. É convenção de unidade da coluna importada, e
  quem prepara o CSV precisa saber dela.
- **R02 não trata conjunto vazio de CSTs compatíveis como "nenhum CST é
  admitido".** Poderia ser lido assim, o que tornaria todo item um achado; mas
  coluna preenchida em branco é indistinguível de coluna que ninguém preencheu, e
  transformar essa dúvida em acusação é o oposto do que uma auditoria faz. Fica
  `NAO_AVALIADO`, visível no relatório e corrigível na carga.
- Testes de regra usam um construtor abreviado de `ItemDocumento` que o modelo de
  produção recusa (D002). Ele existe apenas em `src/test`, e o padrão de todo
  campo opcional nele é `Optional.empty()` — nenhum campo aparece preenchido sem
  que o teste tenha pedido, e nenhum zero surge de padrão.

---

## D005 — A fronteira do XML: leiaute gerado, ausência preservada, participante pseudonimizado

**Etapa:** 4 — Leitura de XML e normalização
**Status:** Aceita

### Contexto

Esta etapa liga o sistema ao mundo. Até aqui o domínio recebia dados montados à
mão em teste; agora ele recebe o que uma empresa real emitiu. A fronteira entre
o arquivo e o modelo é onde quatro coisas dão errado ao mesmo tempo, e todas as
quatro por conveniência.

**Primeira: o leiaute vira código.** Escrever o parser à mão significa digitar
`getElementsByTagName("vBC")` e afirmar, em Java, o que a norma diz que o
documento contém. Cada nome de elemento escrito à mão é uma afirmação sobre o
leiaute que ninguém conferiu, e que envelhece na primeira nota técnica. O mesmo
vale para "quase à mão": biblioteca genérica de XML com caminhos em texto.

**Segunda: ausência vira zero.** Toda biblioteca de XML devolve `null` para
elemento que não veio, e `null` vira `0` na primeira conta ou na primeira coluna
`NOT NULL DEFAULT 0`. D002 existe justamente para separar "o contribuinte não
declarou" de "o contribuinte declarou zero", e é aqui — no ponto onde o dado
entra — que essa distinção se perde ou se preserva. Perdida aqui, nenhuma regra
adiante consegue recuperá-la.

**Terceira: o CNPJ entra junto.** O documento traz CNPJ, CPF, razão social e
endereço de quem emitiu e de quem recebeu. O repositório processa notas reais de
uma empresa. Se esses valores atravessarem a fronteira, passam a existir em todo
objeto, todo log, toda mensagem de erro e todo relatório do sistema.

**Quarta: um arquivo ruim derruba o lote.** Acervo real tem arquivo truncado
pela metade, arquivo que na verdade é um evento de cancelamento, arquivo com
campo fora da forma esperada. Um laço que lê tudo e explode no primeiro problema
processa quinhentos documentos e não entrega nenhum.

### Decisão

**O leiaute não é escrito, é gerado.** As classes de leitura vêm do XSD oficial
do Portal Nacional da NF-e, compilado pelo `jaxb2-maven-plugin` para o pacote
`infraestrutura.xml.gerado` em `target/generated-sources`. Nenhum nome de
elemento do documento fiscal é digitado neste repositório fora do XSD — a
exceção é o próprio ponto de tradução, e ela é única e localizada.

Os XSD **não são versionados por padrão** e não são reproduzidos aqui: quem
clona baixa o Pacote de Liberação do Portal e copia seis arquivos para
`src/main/resources/schemas`, conforme o `LEIAME.md` de lá. Só os dois esquemas
raiz — `procNFe_v4.00.xsd` e `nfe_v4.00.xsd` — entram na configuração do plugin;
os outros quatro chegam por `xs:include`. Listar todos faria o XJC compilar o
mesmo esquema duas vezes e falhar com colisão de nomes na `ObjectFactory`.

**As classes geradas param na fronteira.** Só `infraestrutura.xml` as enxerga, e
há teste (`ClassesGeradasNaoVazam`) que varre o código de produção fora desse
pacote e falha se alguém importar o pacote gerado, `jakarta.xml.bind` ou
`javax.xml.stream`. Sem isso, o primeiro caso de uso que aceitar um `TNFe` por
parâmetro transforma a versão do esquema em parte da assinatura do sistema.

**Ausência atravessa como ausência.** Campo que não veio e campo em branco viram
`Optional.empty()`; campo declarado como `0` vira `Optional.of(ZERO)`. Os valores
são construídos a partir do texto do XML, o que preserva a escala declarada —
`99.99` e `9.9900` chegam ao domínio com as casas que o documento escreveu.
Percentual entra como declarado, sem conversão para fração.

**Onde o leiaute traz um campo e o domínio tem dois, o valor é repetido.** O
grupo `IBSCBS` do item declara um `CST`, um `cClassTrib` e um `vBC` únicos,
válidos para os dois tributos; `ItemDocumento` tem campo separado para IBS e
para CBS. A normalização preenche os dois com o valor declarado, porque foi isso
que o documento afirmou dos dois. Deixar o lado da CBS vazio faria as regras
apontarem ausência de campo que o contribuinte preencheu — apontamento falso, que
é o pior defeito possível numa ferramenta de auditoria.

**Participante só entra pseudonimizado, e o sal vem de fora.** CNPJ e CPF passam
por SHA-256 com um sal de instalação que **não tem valor padrão**: sem ele
configurado, o sistema para. Sal fixo em código estaria no repositório e no jar,
e o espaço de CNPJ é pequeno o bastante para ser percorrido inteiro por força
bruta — pseudônimo com sal público é reversível, e portanto não é
pseudonimização. A garantia é verificada por varredura: o teste percorre o objeto
normalizado inteiro por reflexão, campo a campo, dentro de `Optional` e de lista,
e falha se encontrar o identificador que entrou pelo XML.

**A chave de acesso é preservada como veio, e isso tem preço.** Ela é a
identidade do documento auditado: é o que liga o apontamento ao arquivo e é o que
o contribuinte usa para conferir. Também carrega o CNPJ do emitente nas posições
intermediárias, por definição do leiaute. Logo: **documento com participantes
pseudonimizados não é documento anônimo.** A varredura ignora a chave, de
propósito e com comentário no teste, para não fingir uma garantia que o sistema
não dá.

**Documento que o domínio não representa é recusado inteiro.** Chave com 43
dígitos, NCM fora da forma, data ilegível: a exceção sobe e o documento não é
normalizado. Não há conserto silencioso nem descarte de campo — um documento
representado pela metade entraria no relatório parecendo íntegro.

**O lote registra e continua.** Cada arquivo é lido e fechado antes do seguinte;
falha em um vira `FalhaDeLeitura` — origem, tipo do erro e motivo — e o lote
segue. A única exceção é a falta do sal, que é erro de configuração da instalação
e vale para todos os arquivos. A ordem de processamento sai do nome do arquivo, e
não da listagem do sistema de arquivos, para que dois relatórios do mesmo acervo
possam ser comparados linha a linha.

**Entrada é tratada como não confiável.** DTD e entidades externas estão
desligados no leitor. Sem isso, um documento com `<!DOCTYPE>` faria o processo
abrir arquivo local ou fazer requisição de rede em nome de quem rodou a
auditoria.

### Consequência

Ganhos:

- Trocar de versão de leiaute é baixar outro pacote de XSD e recompilar. Nenhum
  nome de campo do documento fiscal está escrito à mão para ser caçado depois.
- A distinção entre omissão e declaração sobrevive à entrada, que era o único
  ponto onde D002 ainda podia ser perdida sem ninguém ver.
- Não existe caminho pelo qual CNPJ ou CPF cheguem ao domínio, e a afirmação é
  testada por varredura, não por leitura de código.
- Um acervo de dezenas de milhares de notas passa pelo leitor sem caber na
  memória, e o que não pôde ser lido aparece contado e nomeado.

Custos aceitos:

- **O projeto não compila logo depois do clone.** Falta baixar os XSD. É o preço
  de não versionar esquema de terceiro por conta própria; o `LEIAME.md` diz
  exatamente quais arquivos e de onde, e a falha do build aponta o diretório.
- **O caminho do projeto não pode conter acento.** O XJC monta a URI base do
  esquema sem codificar caracteres não-ASCII, e os `xs:include` relativos deixam
  de resolver — em `.../Área de Trabalho/...` a geração falha dizendo que não
  achou `leiauteNFe_v4.00.xsd`. Não é defeito do plugin: acontece igual chamando
  o XJC direto. Espaço no caminho é inofensivo; acento não.
- **NCM de dois dígitos, que o leiaute admite, faz o documento inteiro ser
  recusado**, porque `Ncm` exige oito. Hoje isso vira falha registrada e visível.
  Aceitar esses documentos exige mexer no domínio da Etapa 1, e essa decisão não
  foi tomada aqui.
- **A repetição do CST e da base nos dois tributos é leitura do leiaute atual.**
  Se uma nota técnica passar a declarar CST próprio para a CBS, este é o ponto
  que muda.
- **`indicadorDestinatario` foi mapeado para `dest/indIEDest`** e `crtEmitente`
  para `emit/CRT`. O primeiro é interpretação do nome escolhido na Etapa 1, que
  não registrou de qual campo do leiaute vinha.
- **O leitor de lote captura `RuntimeException` por arquivo.** Erro de
  programação também vira falha registrada, em vez de subir. Fica visível no
  relatório com o nome da exceção, mas não interrompe o lote.
- Os XML sintéticos de teste caem no bloqueio de `*.xml` do `.gitignore` e
  precisam de `git add -f`, um a um. É intencional, e vale a conferida.

---

## D006 — Persistência sem anotação no domínio, e apontamento identificado pelo que aponta

**Etapa:** 5 — Persistência e orquestração
**Status:** Aceita

### Contexto

A partir desta etapa o sistema guarda o que auditou. Isso trouxe três problemas
que não existiam enquanto tudo era memória.

**O primeiro é o caminho fácil do JPA.** Anotar `Documento`, `ItemDocumento` e
`Achado` com `@Entity` faria a persistência quase desaparecer. Também faria o
domínio depender de `jakarta.persistence`, o que D001 proíbe — e não por
purismo: o modelo do domínio é construído sobre distinções que o mapeamento
automático apaga com naturalidade. `Optional.empty()` vira `null` vira zero;
`BigDecimal` com escala declarada vira `numeric(19,2)`; tipo selado com três
variantes vira uma coluna de texto sem quem saiba reconstruí-lo.

**O segundo é o reprocessamento.** Um acervo fiscal é auditado várias vezes: o
catálogo muda, a regra ganha versão, o lote é reprocessado por segurança. Se o
apontamento for identificado pela linha em que foi gravado, cada rodada cria
apontamento novo, e o trabalho humano de examinar cada um se perde a cada
rodada. Quem audita para de confiar no relatório na segunda semana.

**O terceiro é a tratativa.** Um apontamento examinado por uma pessoa recebe uma
decisão — procede, ou não procede — e uma justificativa. Essa decisão precisa
sobreviver ao reprocessamento. Mas não pode sobreviver a *qualquer* coisa: se a
regra que gerou o apontamento mudar de critério, a justificativa antiga passa a
responder a uma pergunta que não está mais sendo feita.

### Decisão

**Entidades JPA separadas, em `infraestrutura/persistencia`, com mapeadores
escritos à mão.** O domínio continua sem uma única anotação. O preço são cerca
de quinze classes de entidade e quatro mapeadores; o retorno é que cada
conversão entre ausência e nulo é uma linha visível, que alguém escreveu de
propósito e que um teste cobre.

**As migrations criam as tabelas vazias.** Catálogo (as quatro tabelas
normativas da Etapa 2 e suas tabelas filhas), documento, item, apontamento,
evidência, execução e tratativa. Nenhum `INSERT` de dado normativo, em nenhuma
migration, em nenhum ambiente. Colunas de código de origem normativa são `text`,
sem limite: um `varchar(N)` seria uma afirmação sobre quantos caracteres a norma
admite. Colunas monetárias são `numeric` sem precisão declarada, porque
PostgreSQL preserva a escala exata do valor gravado e, para a auditoria, "0" e
"0,00" não são o mesmo registro.

**A identidade do apontamento é o que ele aponta**, não a linha:
`(resumo do item, identificador da regra, versão da regra)`. O resumo do item é
um SHA-256 sobre a chave de acesso, o número do item e todos os campos
declarados, com marca própria para campo ausente e preservando a escala dos
valores. Reprocessar o mesmo lote reencontra a linha, atualiza o conteúdo,
avança a última execução que a viu e mantém a primeira detecção.

**A tratativa usa exatamente essa chave, e não tem chave estrangeira para o
apontamento.** É o que a faz sobreviver ao apontamento ser regravado. E,
**deliberadamente, a versão da regra faz parte da chave**: se a regra mudar de
versão, a tratativa antiga não é encontrada e **o apontamento reabre**. A
tratativa antiga não é apagada — fica no banco, presa à versão em que foi dada.
Mudar o conteúdo do item tem o mesmo efeito, pelo mesmo motivo.

**A execução é sempre nova.** Cada rodada é um fato distinto, com hora, resumo
da entrada, versão do catálogo, versão do conjunto de regras, quantidade de
documentos e de itens, e contagem de apontamentos por severidade e por regra —
essas contagens incluindo zero, para que regra que rodou e nada encontrou não
suma do relatório.

**A interface de uso é uma linha de comando, não uma API REST.** Quatro
comandos: `importar-catalogo`, `auditar`, `listar-achados`, `tratar-achado`. Não
há segundo sistema chamando, não há sessão e não há concorrência entre usuários;
uma API traria autenticação, autorização, versionamento de contrato e superfície
de exposição de documento fiscal real, tudo isso sem nenhum consumidor.

**O catálogo gravado é carregado inteiro para a memória a cada rodada**, e
consultado pelos repositórios em memória da Etapa 2. É neles que mora a
resolução por vigência e a recusa de vigências sobrepostas; reimplementar isso
em SQL criaria duas versões da mesma regra, com risco de divergirem.

**Duas coisas entraram além do que a etapa pedia**, porque sem elas o resto não
funciona: a tabela `carga_catalogo`, que identifica cada importação e dá à
execução o `versaoCatalogo` que ela precisa registrar; e a tabela
`cobertura_catalogo`, que guarda a cobertura declarada por tabela — sem ela o
`ConjuntoRegras` não pode ser montado, e silêncio do catálogo voltaria a se
confundir com tabela não carregada (D004). A cobertura entra por um quinto
arquivo CSV, `cobertura.csv`.

**A tolerância de valor da regra R05 é configuração obrigatória, sem padrão.**
Não é conteúdo normativo — é a escolha de quem audita sobre quanta diferença de
arredondamento não merece apontamento. Escolher por conta própria seria decidir,
em nome do usuário, quantos centavos ficam invisíveis no relatório.

### Consequência

Ganhos:

- Reprocessar o mesmo lote não duplica apontamento e não perde tratativa, e isso
  é provado contra um PostgreSQL de verdade, não contra um dublê.
- O domínio continua testável sem contexto Spring e sem banco: a maior parte dos
  testes do projeto roda em segundos e sem Docker.
- Um relatório antigo continua dizendo contra qual catálogo e qual conjunto de
  regras foi produzido, mesmo depois de novas importações.
- O banco recusa, por restrição de formato, qualquer coisa que não seja resumo
  criptográfico nas colunas de participante. É a segunda barreira contra dado
  pessoal em texto claro, depois da do domínio.

Custos aceitos:

- **Cerca de quinze classes de entidade e quatro mapeadores de código
  repetitivo.** É o preço direto de D001, e é conhecido.
- **O Hibernate roda com `ddl-auto=none`.** A validação automática reclama de
  detalhe de tipo que aqui é deliberado — `numeric` sem precisão —, e falharia na
  subida por um motivo que não é defeito. Quem garante que entidade e migration
  concordam é o teste de integração, que grava e lê todas as tabelas.
- **Sem Docker o teste de integração se desabilita em vez de falhar.** Quem clona
  o projeto para ler não precisa de Docker; quem for mexer na persistência
  precisa, e o teste desabilitado aparece no resumo do Maven — o que também
  significa que uma quebra de persistência passa despercebida em máquina sem
  Docker.
- **Apontamento sobre o documento inteiro ainda não tem gravação.** Nenhuma das
  sete regras produz um — o motor percorre itens —, e a identidade gravada é o
  resumo do item. Se uma regra de documento surgir, o serviço de auditoria falha
  com mensagem explícita em vez de violar restrição do banco em silêncio. Criar
  agora uma identidade para apontamento que não existe seria ponto de extensão
  antecipado.
- **Cada importação de catálogo acumula uma carga inteira no banco.** As antigas
  não são apagadas, para que relatórios produzidos contra elas continuem
  conferíveis. Não há comando para limpá-las.
- **O catálogo inteiro vai para a memória a cada rodada.** É aceitável porque um
  catálogo normativo é pequeno perto de um acervo de notas, e porque a carga
  acontece uma vez por rodada, não uma por documento — mas é um limite real.
- **A origem do lote é lida duas vezes**, uma para o resumo da entrada e outra
  para os documentos. Derivar o resumo dos documentos lidos não serviria: ele
  precisa descrever o que foi apresentado ao sistema, inclusive o que não pôde
  ser lido.
- **Reabrir apontamento a cada mudança de versão de regra vai gerar retrabalho
  visível.** É o comportamento pretendido, e é a parte desta decisão que mais
  provavelmente será questionada. A alternativa — deixar a decisão antiga valer
  para o critério novo — faria uma justificativa silenciar um apontamento que ela
  nunca examinou, e isso é pior.

---

## D007 — O papel de trabalho: identificação no topo, documento sem participante

**Etapa:** 6 — Papel de trabalho em xlsx
**Status:** Aceita

### Contexto

Tudo o que o sistema fez até aqui — ler XML, resolver catálogo por vigência,
aplicar regra, gravar apontamento — só vira auditoria quando uma pessoa consegue
olhar uma linha e decidir se ela procede. A saída é o produto; o resto é meio.

Três problemas apareceram ao construí-la.

**O primeiro é o que a planilha responde meses depois.** Um arquivo encontrado
numa pasta compartilhada em novembro precisa dizer contra qual catálogo e com
que versão de regras foi produzido. Sem isso, um apontamento que hoje não
procede mais — porque a tabela mudou — é indistinguível de um erro do sistema, e
a conversa termina em "não sei, roda de novo".

**O segundo é a tensão entre pseudonimizar e rastrear.** A chave de acesso não é
um identificador neutro: os dígitos dela carregam o CNPJ do emitente. Exportá-la
é exportar o CNPJ com um passo a mais de trabalho para lê-lo — e a planilha é
justamente o artefato que sai da máquina e vai por e-mail. Mas trocar a chave por
um resumo criptográfico, sozinho, deixa a linha sem como chegar à nota: o critério
de pronto da etapa é que o achado leve ao documento *sem consultar o banco*, e um
hash de 64 caracteres não leva a lugar nenhum sem o sistema aberto ao lado.

**O terceiro é que os `NAO_AVALIADO` não existiam.** A Etapa 5 gravou só a
contagem, e o motivo de cada um se perdia no fim da rodada. Sem eles não há aba
de não avaliados nem motivos agrupados — e um lote em que nada pôde ser avaliado
sairia com cara de lote limpo.

### Decisão

**Apache POI, xlsx, três abas: Resumo, Achados e Não avaliados.** A escrita é em
fluxo (`SXSSFWorkbook`), com janela de 500 linhas: um acervo real produz dezenas
de milhares de apontamentos, e a alternativa carrega a planilha inteira na
memória.

**A identificação da execução abre o Resumo, sem nada acima dela.** Execução,
data e hora, versão do catálogo, versão do conjunto de regras, resumo da entrada,
documentos e itens auditados. O leiaute é testado por índice de linha, de modo
que empurrar o bloco para o meio da aba quebra o teste.

**O documento aparece pelo pseudônimo da chave — calculado com o mesmo sal de
instalação de emitente e destinatário — acompanhado de modelo, série, número,
data de emissão e UF.** Nenhum desses cinco é dado de participante: série e
número são a numeração sequencial do próprio emitente, e a data e a UF situam a
operação. Juntos localizam a nota no ERP da empresa; separados do CNPJ, não
identificam ninguém. É o que resolve a tensão do segundo problema sem afrouxar a
regra.

**Os `NAO_AVALIADO` passaram a ser gravados**, numa tabela nova
(`avaliacao_nao_concluida`), com o motivo que a própria regra escreveu.
Diferente do apontamento, **não são deduplicados**: não concluir é fato da
rodada, não do documento. A mesma regra sobre o mesmo item pode não concluir hoje
por falta de tabela no catálogo e concluir amanhã, e o papel de trabalho de cada
execução tem de mostrar o que valia na hora dela.

**Entrou também `achado_da_execucao`**, ligando execução a apontamento. A linha
do apontamento guarda só a primeira e a última execução que o viram; sem o
vínculo, reemitir o papel de trabalho de uma rodada antiga traria os apontamentos
da rodada mais recente, e as contagens do Resumo não bateriam com as linhas da
aba de achados. Uma planilha internamente contraditória é pior que nenhuma.

**Uma linha por achado, com as evidências alinhadas dentro da célula.** Um
apontamento pode ter várias evidências; as colunas de campo, valor encontrado e
valor esperado recebem uma linha cada, na mesma ordem, de modo que a enésima
linha de uma corresponde à enésima das outras. Quem confere quer contar
apontamentos, não evidências.

**Ausência é escrita, nunca deixada em branco.** Campo que não veio no documento
vira `(não informado)`; regra sem valor de referência a opor vira
`(sem referência)`; vigência sem fim vira `(sem fim declarado)`; apontamento sem
decisão vira `ABERTO`. Numa planilha lida meses depois, célula vazia é
indistinguível de célula que ninguém preencheu — e a diferença entre "não veio" e
"veio zero" é o eixo do sistema inteiro desde D002.

**O `exportar` é sempre de uma execução identificada**, nunca "do banco".
Exportar os apontamentos atuais produziria uma planilha sem data de corte,
impossível de reconciliar com outra emitida uma semana depois.

### Consequência

Ganhos:

- Um apontamento na planilha leva ao documento, ao item, ao campo, ao valor
  declarado, ao esperado e ao dispositivo legal sem abrir o sistema.
- Nenhum identificador em texto claro sai na exportação, e isso é verificado
  varrendo o arquivo descompactado — não as células —, de modo que um vazamento
  em nome de aba ou propriedade do documento também apareceria.
- O papel de trabalho de qualquer rodada pode ser reemitido, com os apontamentos
  daquela rodada e a identificação que ela tinha.
- Duas emissões do mesmo papel de trabalho saem iguais: as contagens por regra
  são ordenadas pelo identificador, e não pela ordem de iteração do mapa.

Custos aceitos:

- **Quatro arquivos da Etapa 5 mudaram**, com autorização: `ResultadoDaAuditoria`
  passou a carregar a lista de não concluídas em vez da contagem,
  `ServicoDeAuditoria` a recolhê-las, e `RepositorioDaAuditoria(NoBanco)` a
  gravá-las. `DocumentoEntidade` ganhou acessores de leitura.
- **`avaliacao_nao_concluida` cresce a cada rodada** e não é deduplicada. Num
  acervo grande com catálogo incompleto, ela será a maior tabela do banco. É o
  preço de conseguir reemitir o papel de trabalho de uma rodada antiga.
- **Série e número do documento aparecem na planilha.** Não identificam
  participante, mas identificam a operação. Se o arquivo for tratado como
  público, isso precisa ser reconsiderado — a decisão aqui é que o papel de
  trabalho é documento interno de auditoria.
- **A ordem das contagens por regra é alfabética**, e não a ordem de declaração
  do conjunto. Com identificadores `R01` a `R07` as duas coincidem; com uma regra
  chamada de outro jeito, deixariam de coincidir.
- **O leiaute do Resumo está preso a índices de linha no teste.** Mudar o bloco
  quebra o teste de propósito, mas também obriga a mexer nele em toda alteração
  de leiaute.
- **A largura das colunas é fixa.** O cálculo automático exige ter todas as
  linhas em memória, que é justamente o que a escrita em fluxo evita.
- **`ComandoAuditar`, da Etapa 5, continua imprimindo as contagens por regra na
  ordem de iteração de um `Map` imutável**, que a JVM embaralha a cada execução.
  A correção não foi feita porque está fora do que esta etapa autorizou mexer.

---

## D008 — Acurácia medida por gabarito, com o não avaliado fora das métricas

**Etapa:** 7 — Harness de avaliação
**Status:** Aceita

### Contexto

Até aqui o sistema afirma coisas sobre documentos fiscais. Esta etapa responde a
outra pergunta, que é a contribuição do trabalho: **quanto dessas afirmações se
sustenta**. A única resposta honesta vem de comparar a saída do motor com o
julgamento de uma pessoa sobre os mesmos itens.

Três problemas apareceram, e o segundo é o eixo da etapa.

**O primeiro é de onde tirar a saída do motor.** Ler os apontamentos gravados
pela última auditoria seria o caminho curto e está errado: o banco não guarda
avaliação conforme. A Etapa 5 grava apontamento e a Etapa 6 acrescentou as não
concluídas; a regra que se aplicou por inteiro e nada encontrou não deixa linha
nenhuma. Como são justamente essas que formam os verdadeiros negativos — e, por
diferença, os falsos negativos —, medir pelo banco tornaria metade da matriz de
confusão inobservável.

**O segundo é o que fazer com `NAO_AVALIADO`.** Ele não é acerto e não é erro.
Contá-lo como erro puniria o sistema exatamente por aquilo que o resto do projeto
existe para garantir: dizer "não sei" em vez de dizer "está certo". Contá-lo como
acerto é pior, e é o defeito silencioso que esta etapa tem de tornar impossível —
com ele, um sistema que não avalia nada teria acurácia perfeita, e o número que
sustenta o TCC seria uma mentira aritmeticamente correta.

**O terceiro é o que responder quando não há denominador.** Se o motor não
apontou nada entre as linhas medidas, `VP + FP` é zero e a precisão é uma divisão
por zero. Devolver 1 afirma "de tudo o que o sistema apontou, tudo procedia" a
respeito de um sistema que não apontou nada. Devolver 0 afirma o contrário, e é
igualmente falso.

### Decisão

**O gabarito é um CSV rotulado à mão, com quatro colunas obrigatórias**:
`chave_documento`, `numero_item`, `regra_id` e `rotulo_esperado`. O rótulo é
`ACHADO` ou `CONFORME`, e **`NAO_AVALIADO` é recusado explicitamente**: quem
rotula responde sobre o documento, não sobre o sistema. Rotular "aqui o motor não
vai conseguir julgar" mediria a ferramenta contra a expectativa que já se tem
dela.

**Seis desfechos, quatro dos quais entram na métrica.** `Desfecho` é um enum com
os quatro quadrantes da matriz de confusão mais `NAO_AVALIADO` — o motor não pôde
julgar — e `SEM_AVALIACAO` — o gabarito aponta item que o motor não viu.
`entraNaMetrica()` é o único lugar do sistema em que se decide o que conta como
medição, e existe isolado para que a decisão não fique espalhada em somas.

**Precisão e recall se calculam só sobre os quatro quadrantes.** As duas outras
contagens não aparecem em nenhum denominador dessas fórmulas. Elas aparecem em
**cobertura**, que é métrica própria: avaliados sobre o total do gabarito.
Cobertura baixa com precisão alta é resultado legítimo — o sistema acerta o que
julga e julga pouco — e só é legível porque os dois números são reportados lado
a lado.

**Métrica sem denominador é `Metrica.Indefinida`, com o motivo.** O tipo é selado
em duas variantes pelo mesmo raciocínio de `Avaliacao`: um `BigDecimal` obrigaria
alguém, em algum ponto, a escolher um número para o caso sem denominador. Na
saída ela vira `(indefinida)` — nunca campo em branco, que quem tem pressa lê
como zero.

**F1 é calculado como `2VP / (2VP + FP + FN)`**, algebricamente igual à média
harmônica onde as duas são definidas, e **só é definido quando precisão e recall
também são**. A fórmula direta daria zero em casos em que a precisão não existe,
e um resumo não pode afirmar mais que os números que resume.

**O consolidado soma células, não faz média das métricas por regra.** A média
trataria uma regra com três linhas rotuladas igual a uma com duzentas, e
obrigaria a decidir o que fazer com as regras de métrica indefinida — decisão sem
resposta defensável. Somando células, uma regra sem linha no gabarito contribui
com zero e não distorce nada.

**Toda regra do conjunto ganha linha no relatório**, inclusive as que o gabarito
não cita, zeradas e com métricas indefinidas. Omiti-las faria o relatório parecer
completo quando não é.

**O harness roda o motor de novo e não persiste nada.** Não grava execução, não
entra no histórico e não vira papel de trabalho. Medir não é auditar, e uma
opção "não grave" num serviço que grava é a forma mais discreta de um dia gravar
por engano — por isso são dois serviços, e não um com sinalizador.

**Duas recusas duras, ambas para não transformar erro de digitação em conclusão
sobre o acervo:** gabarito que rotula o mesmo endereço duas vezes falha na carga,
como falha uma vigência sobreposta no catálogo (D003); e gabarito que cita
`regra_id` fora do conjunto falha na comparação, listando as regras conhecidas —
sem isso, um `R0X` viraria dezenas de `SEM_AVALIACAO` e o relatório acusaria o
acervo em vez do arquivo.

**O `LeitorCsv` da Etapa 2 mudou de `infraestrutura.catalogo` para
`infraestrutura.csv`, com autorização.** O gabarito usa o mesmo formato, e
duplicar o analisador significaria duas convenções de CSV divergindo com o tempo.
Como o leitor não sabe de que assunto é o arquivo, ele passou a receber
`RecusaDeCsv` — quem o chama diz como nomear a falha. Catálogo malformado
continua produzindo `ImportacaoDeCatalogoInvalida`, com as mesmas mensagens; o
gabarito produz `GabaritoInvalido`.

### Consequência

Ganhos:

- Precisão, recall e F1 por regra e consolidados, em `BigDecimal` com quatro
  casas, contra um gabarito rotulado à mão — o resultado empírico do trabalho.
- **É impossível `NAO_AVALIADO` inflar métrica.** A separação está no tipo, não
  numa condição: as contagens que não entram na medição são campos distintos de
  `ContagemDeAcuracia`, e nenhuma fórmula de precisão ou recall as menciona.
- O confronto é função pura sobre duas listas, conferível à mão em teste, sem
  leitura de arquivo nem banco no caminho.
- O relatório diz contra qual catálogo e qual versão de regras foi obtido. Um
  número de acurácia sem procedência não sustenta afirmação nenhuma.
- Desalinhamento entre gabarito e acervo aparece com endereço, e não como métrica
  ruim.

Custos aceitos:

- **Repetição deliberada** dos quatro primeiros passos de `ServicoDeAuditoria`.
  Os dois serviços divergem no passo seguinte, e uni-los exigiria o sinalizador
  que a decisão recusa.
- **O harness relê o acervo inteiro a cada medição.** Com trezentos itens é
  irrelevante; com um acervo grande, é uma auditoria completa só para medir.
- **Sete arquivos da Etapa 2 mudaram**, com autorização: `LeitorCsv` e `LinhaCsv`
  mudaram de pacote e ficaram públicos, os quatro importadores e
  `LeitorDeCatalogoEmCsv` passaram a informar como recusam, e `LeitorCsvTest`
  acompanhou a assinatura. `ConfiguracaoDaAuditoria`, da Etapa 5, ganhou dois
  `@Bean`.
- **`ComandoAvaliarAcuracia` traduz as próprias recusas em `UsoInvalido`**, em
  vez de `LinhaDeComando` ganhar mais um `catch`. O efeito colateral é útil —
  gabarito malformado sai acompanhado do formato esperado — mas a decisão sobre
  onde tratar erro de arquivo passa a ter dois lugares.
- **A chave de acesso aparece em texto claro no terminal e no comentário do CSV
  de acurácia**, para os endereços que o motor não avaliou. É o mesmo que
  `listar-achados` já faz, e quem mede escreveu o gabarito com essas chaves — mas
  não é o tratamento que a D007 dá ao papel de trabalho, e a diferença é
  deliberada: o relatório de acurácia é insumo de quem opera, não artefato que
  circula.
- **O consolidado é micro, não macro.** Uma regra com muitas linhas rotuladas
  domina o número consolidado. As linhas por regra estão logo acima, mas quem
  citar só o consolidado estará citando uma média ponderada pelo esforço de
  rotulagem.

---
