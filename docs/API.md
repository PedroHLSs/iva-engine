# API HTTP

A API e a interface web sobem juntas com o comando `servir` (ou com o jar chamado
sem argumento; ver [COMANDOS.md](COMANDOS.md#servir)). As telas estão descritas
em [INTERFACE-WEB.md](INTERFACE-WEB.md).

## Fronteira

- **Escuta só em `127.0.0.1`**, na porta de `AUDITORIA_API_PORTA` (padrão
  8080). Não há HTTPS, e por isso o bind não deve ser aberto: a senha trafegaria
  em texto claro pela rede. Trocar `server.address` por `0.0.0.0` publica
  documento fiscal real e a capacidade de gravar para a rede inteira.
- Sem CORS. Sem multiempresa.
- **Toda chamada exige sessão**, exceto abrir a sessão e pedir o token
  contra falsificação.
- Respostas em JSON. **Nenhum campo é omitido:** ausência é `null` com um campo
  irmão dizendo por quê. Valor monetário sai como texto, para a escala declarada
  não se perder no `JSON.parse` do cliente.

## Sessão

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/sessao/csrf` | qualquer um | devolve `{cabecalho, token}`: o token que toda escrita manda de volta naquele cabeçalho |
| `POST /api/sessao` | qualquer um | entra: corpo `{"login", "senha"}` |
| `GET /api/sessao` | logado | quem está logado |
| `DELETE /api/sessao` | logado | sai |
| `PUT /api/sessao/senha` | logado | troca a própria senha: `{"senhaAtual", "senhaNova"}` |

A sessão fica no servidor; o navegador guarda só o identificador, num cookie
`http-only` e `same-site=strict`. Duração em `AUDITORIA_SESSAO_DURACAO` (padrão
`30m`). Não há "lembrar-me" nem recuperação por e-mail; o primeiro administrador
e a recuperação de acesso são feitos por `criar-administrador`. A senha é
guardada com BCrypt e o usuário é relido do banco a cada pedido.

## Perfis

| Perfil | Pode |
|---|---|
| Administrador | tudo o que o fiscal faz, mais usuários e cargas de catálogo |
| Fiscal | enviar nota, corrigir análise, registrar tratativa, medir acurácia, ler tudo |
| Consulta | só ler |

A permissão é conferida **no servidor**, endpoint por endpoint, antes de qualquer
controlador, pela matriz em
`src/main/java/br/edu/tcc/auditoria/infraestrutura/seguranca/MatrizDePermissoes.java`.
O que não está na matriz é negado. Esconder o botão na tela é só conveniência.
Execução, análise e apontamento não têm alteração nem exclusão para perfil
nenhum.

## Conferência

| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /api/analises` | fiscal, administrador | envia a nota no campo multipart `arquivo`: um `.xml` ou um `.zip`. Dispara o mesmo processamento do `auditar` |
| `GET /api/analises/{id}` | todos | resumo da análise: os quatro estados, pendências, ilegíveis, versões |
| `GET /api/analises/{id}/produtos` | todos | produtos da análise; `?pagina=0&tamanho=50` |
| `GET /api/analises/{id}/produtos/{endereco}` | todos | detalhe de um produto: verificações, base normativa ao lado, comparação entre declarado e indicado |
| `GET /api/analises/{id}/grupos` | todos | agrupamento por NCM + cClassTrib + situação; `?ordem=VALOR_DOS_PRODUTOS` (padrão) ou `QUANTIDADE_DE_PRODUTOS` |
| `GET /api/analises/{id}/grupos/produtos` | todos | produtos de um grupo; `?ncm=&cClassTrib=&situacao=&pagina=0&tamanho=50` |
| `GET /api/base-tributaria` | todos | o que a base carregada indica numa data; `?data=aaaa-mm-dd&ncm=&cClassTrib=` |
| `POST /api/analises/{id}/correcoes` | fiscal, administrador | corrige a entrada: cria uma análise nova, ligada a esta, com o campo `arquivo`. A anterior não muda |
| `GET /api/analises/{id}/vinculos` | todos | as correções ligadas à análise |

- **`.rar` não é aceito**, e a recusa diz isso: não há biblioteca Java confiável
  para RAR5. Compacte em `.zip`. O pacote passa por um guarda antes de qualquer
  leitura: contagem de entradas, tamanho descomprimido **medido**, razão de
  compressão acima de um piso e caminho de entrada que escape do diretório. Os
  limites estão em [CONFIGURACAO.md](CONFIGURACAO.md#envio-de-arquivos).
- **`data` é obrigatória em `/api/base-tributaria`**, sem padrão: o mesmo
  catálogo responde coisas diferentes em datas diferentes, e um "hoje" silencioso
  faria a resposta mudar sozinha de um dia para o outro.
- **O tratamento é resolvido contra a carga que a análise registrou**, na data de
  emissão de cada documento, e não contra o catálogo de hoje.
- **A comparação entre declarado e indicado não emite veredito**; quem julga são
  as sete regras.
- IBS e CBS saem em blocos separados, e as parcelas do IBS não são somadas.
- A ordem por valor dos produtos mede **exposição**, não gravidade, e a resposta
  diz isso.

## Histórico

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/analises` | todos | histórico paginado e filtrado, da mais recente para a mais antiga |
| `GET /api/analises/{id}/autoria` | todos | quem executou, ou "executor não registrado" |

Parâmetros do histórico: `pagina` (padrão 0), `tamanho` (padrão 20), `de` e
`ate` (datas), `situacaoMaisGrave`, `minimoDeDivergencias`,
`maximoDeDivergencias`, `executor` (login) e `semExecutorRegistrado=true`.

- **O filtro de situação é pela situação mais grave presente**, não pela da
  maioria: uma análise com um produto não concluído entra como "não foi possível
  concluir".
- Execução feita pelo `auditar` fica com **executor não registrado** e com a
  contagem de produtos **não registrada** (o `auditar` não grava os itens lidos).
  Os filtros de situação e de quantidade **não excluem** essas execuções, porque
  excluir por um valor não medido esconderia resultado real; com um desses filtros
  ativo, a resposta diz quantas estão incluídas e por quê.

## Visão técnica (por execução)

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/execucoes` | todos | execuções gravadas; `?limite=25` |
| `GET /api/execucoes/{id}` | todos | uma execução, com os desfechos |
| `GET /api/execucoes/{id}/achados` | todos | apontamentos; `?regra=R05&severidade=GRAVE&status=ABERTO&pagina=0&tamanho=50` |
| `GET /api/execucoes/{id}/nao-avaliados` | todos | avaliações não concluídas; `?regra=R05&pagina=0&tamanho=50` |

`severidade` aceita `CRITICA`, `GRAVE`, `MODERADA` e `INFORMATIVA`; `status`
aceita `ABERTO`, `ACEITO` e `REFUTADO`, sem distinguir caixa.

**Os três desfechos aparecem inteiros.** `ACHADO` e `NAO_AVALIADO` são campos
próprios — o segundo com os motivos agrupados por regra —, e cada linha escreve o
próprio `resultado` por extenso. O `CONFORME` é derivado, porque o banco não grava
avaliação conforme, e a resposta traz a conta que o produziu:

```json
"desfechos" : {
  "avaliacoesProduzidas" : 20,
  "comoFoiObtido" : "quantidadeItens 10 x regras aplicadas 2: o motor produz ...",
  "achado" : 1,
  "naoAvaliado" : 3,
  "conforme" : {
    "valor" : 16,
    "derivacao" : "avaliacoesProduzidas 20 - achado 1 - naoAvaliado 3",
    "motivoDaAusencia" : null
  }
}
```

A regra sai pelo nome por extenso (`regraNome`), com o código ao lado
(`regraId`).

## Tratativas

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/achados/{id}/tratativas` | todos | histórico de decisões, com quem decidiu e quando |
| `POST /api/achados/{id}/tratativas` | fiscal, administrador | `{"decisao": "ACEITO" ou "REFUTADO", "justificativa"}` |

O histórico de tratativas só recebe acréscimo.

## Cargas de catálogo

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/cargas` | todos | as cargas, com estado (rascunho ou selada) |
| `GET /api/cargas/{versao}` | todos | uma carga, inclusive o que salvar uma edição faria |
| `POST /api/cargas` | administrador | importa: `versao` (obrigatória) e os CSV no campo multipart `arquivos` |
| `PUT /api/cargas/{versao}` | administrador | edita: `efeitoEsperado`, `versaoNova` e os CSV que mudam em `arquivos` |
| `DELETE /api/cargas/{versao}` | administrador | exclui um rascunho; carga usada é recusada |

- **Importação** segue as mesmas recusas do `importar-catalogo`. Com os cinco
  arquivos obrigatórios é completa. Sem algum deles é parcial: o pedido diz, em
  `cargaDeOrigemEsperada`, `origemImportadaEmEsperada` e
  `origemAlteradaEmEsperada`, a carga mais recente que a tela mostrou, exatamente
  como o `GET /api/cargas` a devolveu. Se outra passou a ser a mais recente, se a
  mesma foi alterada, ou excluída e importada de novo com o mesmo nome, nada é
  gravado e a resposta traz a origem atual.
- **Carga usada por uma análise está selada.** Excluí-la é recusado, com a
  quantidade de análises que dependem dela; editá-la cria uma carga nova
  (`efeitoEsperado=CRIAR_VERSAO_NOVA`), e a original fica intacta, porque cada
  análise é reaberta com a carga que usou. Rascunho, que nunca foi usado, é
  editado no lugar (`ALTERAR_RASCUNHO`) e excluído livremente. Se o efeito mudou
  entre a tela e o pedido, a resposta é 409 e nada é gravado.
- Editar é **substituir tabela por CSV**; não há formulário de linha.

## Acurácia

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/acuracia/previa` | todos | qual carga a medição vai usar e selar |
| `POST /api/acuracia` | fiscal, administrador | mede: `notas` (`.zip`/`.xml`), `gabarito` (`.csv`) e `cargaEsperada` |

Usa o mesmo serviço e o mesmo comparador do `avaliar-acuracia`, sem gravar nada
além do selo da carga. **Medir sela a carga mais recente**; o pedido leva a carga
que a prévia mostrou, e se a mais recente mudou, a resposta é 409, sem medir nem
selar. O consolidado é a soma das células; não há média macro. Métrica sem
denominador sai `(indefinida)`.

## Usuários

| Método e caminho | Quem | O que faz |
|---|---|---|
| `GET /api/usuarios` | administrador | lista |
| `POST /api/usuarios` | administrador | cria: `{"login", "nome", "perfil", "senha"}` |
| `GET /api/usuarios/{id}` | administrador | um usuário |
| `PUT /api/usuarios/{id}` | administrador | altera: `{"nome", "perfil", "ativo", "novaSenha"}` |
| `DELETE /api/usuarios/{id}` | administrador | exclui; quem já registrou tratativa ou correção é **desativado**, não apagado |

`perfil` é `ADMINISTRADOR`, `FISCAL` ou `CONSULTA`. O último administrador ativo
não pode ser excluído, rebaixado nem desativado.

## O que toda resposta de resultado carrega

- **A faixa de procedência do catálogo** — normativo, fictício, parcialmente
  fictício (com a lista das tabelas) ou de procedência não declarada — e o aviso
  de uso.
- **A tolerância da R05 e a origem dela** (configurada ou padrão), ou "não
  registrada" na execução que não a gravou.

## Privacidade

Três campos saem `null`, com o motivo ao lado, a menos que a instalação os
libere:

| Propriedade | Padrão | O que libera |
|---|---|---|
| `auditoria.api.expor-chave-de-acesso` | `false` | a chave de acesso em texto claro, além do pseudônimo |
| `auditoria.api.expor-justificativa` | `false` | o texto da justificativa da tratativa |
| `auditoria.api.expor-descricao-do-produto` | `false` | o `xProd` do XML |

Os dígitos intermediários da chave de acesso são o CNPJ do emitente; a
justificativa é texto livre e pode conter CNPJ ou razão social. São configuração
da instalação, e não parâmetro de consulta: quem consome a API não escolhe quanto
dado pessoal recebe.

**A descrição do produto é a mais sensível das três.** Ela vem do emitente, em
escala, sem revisão, e na prática traz nome de cliente, referência de pedido e
número de contrato. Quem grava substitui sequência de 44 dígitos por um marcador,
mas **nenhuma regra de forma alcança prosa**: "P/ OBRA FULANO" passa. Desligada, a
tela de detalhe mostra a descrição do NCM no catálogo e, no lugar da descrição da
nota, o motivo; a comparação entre as duas — sinal de classificação errada que
nenhuma das duas dá sozinha — só aparece inteira com a propriedade ligada.

**Nenhuma das três afeta a exportação.** A planilha nunca leva chave em texto
claro nem descrição de produto; a justificativa tem o próprio opt-in
(`auditoria.exportacao.expor-justificativa`). As variáveis de ambiente
correspondentes estão em [CONFIGURACAO.md](CONFIGURACAO.md).
