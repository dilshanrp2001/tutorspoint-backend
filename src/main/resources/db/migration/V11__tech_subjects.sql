-- V11__tech_subjects.sql
--
-- Subjects for tutors who teach computing outside the school exam system - HND, BIT and
-- degree students, and adults learning for work. V4 gave them only Computer Programming,
-- so a databases tutor and a web tutor were indistinguishable in the subject filter.
--
-- This is the minimum set for the MVP: the areas students actually seek help with, each
-- broad enough that a tutor can pick it honestly. Individual languages and tools (Java,
-- Python, React, PostgreSQL) are deliberately not subjects - Computer Programming covers
-- them, and keyword search over the headline and bio finds them. Narrower subjects are a
-- later migration once search data shows what people ask for.
--
-- They take the 930s, after Computer Programming (915) and Graphic Design (920), so the
-- 900s block of skills taught outside the exam system stays together.
--
-- No trigger or index work is needed: V7's subject_translations trigger already folds new
-- names into the search document of any profile that teaches them.

INSERT INTO subjects (code, display_order, active, created_at, updated_at)
SELECT v.code, v.display_order, TRUE, NOW(), NOW()
FROM (VALUES
    ('DATABASES',                      930),
    ('WEB_DEVELOPMENT',                935),
    ('MOBILE_APP_DEVELOPMENT',         940),
    ('DATA_STRUCTURES_AND_ALGORITHMS', 945),
    ('SOFTWARE_ENGINEERING',           950),
    ('COMPUTER_NETWORKS',              955),
    ('DATA_SCIENCE_AND_MACHINE_LEARNING', 960),
    ('CYBER_SECURITY',                 965)
) AS v(code, display_order);

-- Sinhala and Tamil follow the terms of the national ICT syllabus where it has one
-- (දත්ත සමුදාය / தரவுத்தளம், ඇල්ගොරිතම / படிமுறை). SQL stays in Latin script: it is a
-- proper noun, like the professional acronyms in V4.
INSERT INTO subject_translations (subject_id, language, name)
SELECT s.id, t.language, t.name
FROM subjects s
JOIN (VALUES
    ('DATABASES',                         'Databases & SQL',                  'දත්ත සමුදාය හා SQL',              'தரவுத்தளமும் SQL உம்'),
    ('WEB_DEVELOPMENT',                   'Web Development',                  'වෙබ් සංවර්ධනය',                   'இணையத்தள அபிவிருத்தி'),
    ('MOBILE_APP_DEVELOPMENT',            'Mobile App Development',           'ජංගම යෙදුම් සංවර්ධනය',            'கைபேசிச் செயலி அபிவிருத்தி'),
    ('DATA_STRUCTURES_AND_ALGORITHMS',    'Data Structures & Algorithms',     'දත්ත ව්‍යුහ හා ඇල්ගොරිතම',         'தரவுக் கட்டமைப்புகளும் படிமுறைகளும்'),
    ('SOFTWARE_ENGINEERING',              'Software Engineering',             'මෘදුකාංග ඉංජිනේරු විද්‍යාව',        'மென்பொருள் பொறியியல்'),
    ('COMPUTER_NETWORKS',                 'Computer Networks',                'පරිගණක ජාල',                      'கணினி வலையமைப்புகள்'),
    ('DATA_SCIENCE_AND_MACHINE_LEARNING', 'Data Science & Machine Learning',  'දත්ත විද්‍යාව හා යන්ත්‍ර ඉගෙනුම',   'தரவு விஞ்ஞானமும் இயந்திரக் கற்றலும்'),
    ('CYBER_SECURITY',                    'Cyber Security',                   'සයිබර් ආරක්ෂණය',                  'சைபர் பாதுகாப்பு')
) AS v(code, en, si, ta) ON v.code = s.code
CROSS JOIN LATERAL (VALUES ('EN', v.en), ('SI', v.si), ('TA', v.ta)) AS t(language, name);
