-- Zonas de reparto propio: Asunción y Gran Asunción.
-- Solo se insertan las que no existen todavía (comparando sin mayúsculas ni tildes, ej. "Capiata" = "Capiatá"),
-- así no se duplican ni se tocan las zonas ya cargadas.
INSERT INTO zones (id, name, description, active, created_at)
SELECT gen_random_uuid(), nueva.name, NULL, true, now()
FROM (VALUES
    ('Asunción'),
    ('Lambaré'),
    ('Fernando de la Mora'),
    ('San Lorenzo'),
    ('Luque'),
    ('Capiatá'),
    ('Limpio'),
    ('Mariano Roque Alonso'),
    ('Villa Elisa'),
    ('Ñemby'),
    ('San Antonio'),
    ('Areguá'),
    ('Itá'),
    ('Itauguá'),
    ('J. Augusto Saldívar'),
    ('Ypané'),
    ('Villeta'),
    ('Guarambaré')
) AS nueva(name)
WHERE NOT EXISTS (
    SELECT 1 FROM zones z
    WHERE translate(lower(z.name), 'áéíóúüñ', 'aeiouun') = translate(lower(nueva.name), 'áéíóúüñ', 'aeiouun')
);
