-- Stock inicial do armazém CENTRO (Beira). Cenários de demonstração em docs/notas-relatorio.md.
INSERT INTO stock (armazem_id, produto_id, quantidade, minimo, maximo) VALUES
    ('CENTRO', 'ARROZ-25',    104, 100, 400),  -- "Pico simultâneo": uma saída de 10 deixa-o abaixo do mínimo
    ('CENTRO', 'FARINHA-25',   90,  80, 300),  -- "perto do mínimo" (90 <= 1,2 x 80)
    ('CENTRO', 'FEIJAO-10',   140,  60, 250),
    ('CENTRO', 'ACUCAR-10',   160,  80, 300),
    ('CENTRO', 'OLEO-5',      150,  60, 250),
    ('CENTRO', 'SABAO-BARRA', 500, 200, 800);
