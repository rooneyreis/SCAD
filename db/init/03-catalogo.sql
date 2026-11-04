-- Catálogo (replicado em todos os fragmentos). Empresas fictícias.

INSERT INTO armazem (id, nome, cidade) VALUES
    ('NORTE',  'Armazém Regional Norte',  'Nampula'),
    ('CENTRO', 'Armazém Regional Centro', 'Beira'),
    ('SUL',    'Armazém Regional Sul',    'Maputo');

INSERT INTO fornecedor (id, nome, protocolo, endpoint) VALUES
    ('GRAO-DOURADO', 'Grão Dourado, Lda',              'REST', '/api/v1/ordens'),
    ('DOCE-VALE',    'Doce Vale Açúcares, Lda',        'REST', '/api/v1/ordens'),
    ('OLEOS-LIMPOPO','Saboaria e Óleos do Limpopo, SA','SOAP', '/ws/fornecedor');

INSERT INTO produto (id, nome, unidade, fornecedor_id, lote) VALUES
    ('ARROZ-25',    'Arroz 25 kg',              'saco',   'GRAO-DOURADO',  500),
    ('FARINHA-25',  'Farinha de milho 25 kg',   'saco',   'GRAO-DOURADO',  400),
    ('FEIJAO-10',   'Feijão manteiga 10 kg',    'saco',   'GRAO-DOURADO',  300),
    ('ACUCAR-10',   'Açúcar castanho 10 kg',    'saco',   'DOCE-VALE',     400),
    ('OLEO-5',      'Óleo alimentar 5 L',       'garrafão','OLEOS-LIMPOPO', 600),
    ('SABAO-BARRA', 'Sabão em barra 400 g',     'barra',  'OLEOS-LIMPOPO', 1000);
