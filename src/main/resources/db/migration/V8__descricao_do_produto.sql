-- ---------------------------------------------------------------------------
-- A descrição do produto, como o emitente a escreveu.
--
-- NENHUM DADO NORMATIVO. O único UPDATE desta migration escreve um marcador
-- técnico de ausência em linhas já existentes, e está explicado abaixo.
--
-- POR QUE A DESCRIÇÃO ENTRA AQUI, E NÃO EM "item_documento"
--
-- Porque ela é o que ESTA análise leu, e não o que está gravado hoje.
-- "item_documento" é sobrescrito a cada reprocessamento (D006); "item_da_execucao"
-- é o registro do que cada análise viu, e já guarda o hash daquela leitura.
--
-- E porque a descrição NÃO entra no hash do item: o resumo de HashDoItem cobre
-- os campos que as regras examinam, e a descrição não é um deles. A consequência
-- é decisiva para o desenho: o mesmo hash pode legitimamente acompanhar duas
-- descrições diferentes — a mesma mercadoria, com os mesmos campos fiscais, e o
-- texto corrigido entre uma emissão e outra. Uma tabela à parte, chaveada pelo
-- hash, teria de escolher uma das duas e apagar a outra. Aqui não existe essa
-- escolha: a descrição é coluna da linha que já identifica aquela leitura, e
-- associação errada é impossível por construção, não por cuidado.
--
-- ATENÇÃO: ESTA É A PRIMEIRA COLUNA DE TEXTO LIVRE VINDO DO EMITENTE.
--
-- O V2 declara que o esquema não tem coluna de dado pessoal em texto claro, e
-- essa afirmação continua valendo para identificador de participante: não há
-- CNPJ, CPF, razão social nem endereço em coluna nenhuma. Mas xProd é campo
-- digitado por quem emitiu, em escala, sem revisão, e na prática vem com nome de
-- cliente, referência de pedido e número de contrato. Guardá-lo é necessário
-- para a tela poder comparar a descrição da nota com a descrição do NCM — que é
-- o sinal de classificação errada que nenhuma das duas dá sozinha —, e a
-- contrapartida são três controles:
--
--   1. quem grava substitui corrida de 44 dígitos pelo marcador, antes daqui;
--   2. a restrição abaixo é a barreira de última instância, como em "origem";
--   3. a exposição por HTTP é opt-in, e a exportação nunca a leva.
--
-- O que nenhuma regra de forma alcança é prosa livre: "P/ OBRA FULANO" passa, e
-- passaria por qualquer verificação automática. Isso está declarado no README
-- como limitação conhecida, e não escondido atrás de uma checagem que daria
-- impressão de cobrir o que não cobre.
-- ---------------------------------------------------------------------------

alter table item_da_execucao
    add column descricao_produto    text,
    add column motivo_sem_descricao text;

-- As linhas gravadas antes desta etapa não têm como recuperar a descrição: o
-- sistema guarda o item, não versões dele. Em vez de deixá-las mudas — nulo nas
-- duas colunas, indistinguível de "o emitente não descreveu" —, a ausência fica
-- escrita. É a mesma disciplina da D002 aplicada a dado já gravado: ausência é
-- um estado, e estado se declara.
--
-- Este UPDATE não escreve conteúdo normativo, não escreve dado de documento e
-- não inventa descrição nenhuma. Ele escreve o motivo de não haver uma.
update item_da_execucao
   set motivo_sem_descricao = 'esta análise é anterior ao registro da descrição do produto, e o '
                              || 'sistema guarda o item, não versões dele: não há como recuperar o '
                              || 'que ela leu'
 where descricao_produto is null
   and motivo_sem_descricao is null;

alter table item_da_execucao
    add constraint item_da_execucao_descricao_explicada
        check ((descricao_produto is not null and motivo_sem_descricao is null)
               or (descricao_produto is null and motivo_sem_descricao is not null)),
    add constraint item_da_execucao_descricao_preenchida
        check (descricao_produto is null or btrim(descricao_produto) <> ''),
    add constraint item_da_execucao_descricao_sem_chave_em_texto_claro
        check (descricao_produto is null or descricao_produto !~ '[0-9]{44}');
