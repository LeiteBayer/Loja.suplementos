-- Catálogo com preço em reais (o armazenamento usa centavos inteiros).
SELECT codigo, nome, preco_centavos / 100.0 AS preco_reais, estoque FROM produtos;

-- Clientes e os respectivos tipos.
SELECT id, identificacao, nome, email, tipo FROM clientes;

-- Vendas com cliente e forma de pagamento.
SELECT v.id, v.data_hora, c.nome, p.tipo, p.parcelas,
       v.subtotal_centavos / 100.0 AS subtotal_reais,
       v.desconto_centavos / 100.0 AS desconto_reais,
       v.total_centavos / 100.0 AS total_reais
FROM vendas v
JOIN clientes c ON c.id = v.cliente_id
JOIN pagamentos p ON p.venda_id = v.id
ORDER BY v.data_hora DESC;

-- Endereços vinculados aos pedidos com entrega.
SELECT venda_id, logradouro, numero, complemento, bairro, cidade, uf, cep
FROM enderecos_entrega;

-- Cartões cadastrados por cliente com número mascarado.
SELECT c.identificacao, ca.titular, '**** ' || ca.final_numero AS cartao, ca.validade
FROM cartoes ca
JOIN clientes c ON c.id = ca.cliente_id;

-- Itens preservam o nome e o preço da época da venda.
SELECT venda_id, produto_codigo, produto_nome, quantidade,
       preco_unitario_centavos / 100.0 AS preco_unitario_reais,
       subtotal_centavos / 100.0 AS subtotal_reais
FROM itens_venda;

-- Validação das chaves estrangeiras (resultado vazio indica ausência de violações).
PRAGMA foreign_key_check;
