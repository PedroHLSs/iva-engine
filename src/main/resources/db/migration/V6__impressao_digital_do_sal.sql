-- ---------------------------------------------------------------------------
-- Impressão digital do sal de instalação.
--
-- O QUE ESTA TABELA GUARDA, E O QUE ELA NÃO GUARDA.
-- Guarda um resumo SHA-256 do sal em uso, com separação de domínio. NÃO guarda
-- o sal, e não pode guardar: sal em banco é sal que vaza junto com o backup, e
-- pseudônimo com sal conhecido é reversível por força bruta (D005).
--
-- POR QUE ELA EXISTE.
-- Trocar o sal não quebra tratativa — a chave da tratativa é o hash do item,
-- que não é salgado, por decisão explícita (D006). O que a troca quebra é a
-- coerência interna do acervo: emitente_pseudonimizado e
-- destinatario_pseudonimizado em `documento` foram calculados com o sal
-- anterior, e o mesmo participante passa a existir sob dois pseudônimos no
-- mesmo acervo. Nenhuma contagem muda, nenhum relatório acusa, e o defeito não
-- aparece em lugar nenhum — é falso negativo silencioso.
--
-- Com esta tabela, a subida compara o sal resolvido com o que produziu o que já
-- está gravado, e recusa em vez de prosseguir.
--
-- SEM INSERT. A tabela nasce vazia. A primeira linha é escrita em tempo de
-- execução, pelo sistema, a partir do sal que a instalação resolveu.
-- ---------------------------------------------------------------------------

create table impressao_digital_do_sal (
    -- Linha única: a instalação tem um sal em uso, não um por linha. A restrição
    -- abaixo impede que uma segunda linha apareça e crie a dúvida sobre qual
    -- delas vale.
    id                            smallint    not null,
    valor                         varchar(64) not null,
    registrada_em                 timestamptz not null,

    -- De onde o sal veio quando esta impressão digital foi registrada. Não
    -- participa da comparação; existe para o diagnóstico poder dizer "a
    -- impressão digital atual foi registrada quando o sal vinha do arquivo
    -- local", que é o que explica uma divergência.
    origem                        text        not null,

    -- Verdadeiro quando a impressão digital foi registrada sobre um acervo que
    -- JÁ TINHA documentos. Nesse caso não foi possível verificar nada: não havia
    -- com o que comparar, e o valor gravado é o do sal daquele momento, assumido
    -- como o correto. A coluna guarda essa ressalva para que o diagnóstico não
    -- afirme uma verificação que não houve.
    adotada_de_acervo_existente   boolean     not null,

    constraint impressao_digital_do_sal_pk primary key (id),
    constraint impressao_digital_do_sal_linha_unica check (id = 1),
    constraint impressao_digital_do_sal_hexadecimal check (valor ~ '^[0-9a-f]{64}$')
);
