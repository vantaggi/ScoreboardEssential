"""Test di scripts/misura-ui.py --blocca (PR 10 della UI Constitution).

Copia i sorgenti dell'interfaccia in una cartella temporanea e ci fa girare lo script: sui sorgenti
di oggi esce con 0, con un colore scritto a mano in un layout esce con 1 e lo nomina, con un quadrante
fuori misura esce con 1. Rosso se --blocca smette di bloccare o se blocca un albero pulito.

    python3 -m unittest discover -s scripts -p 'test_*.py'
"""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

RADICE = Path(__file__).resolve().parent.parent
SCRIPT = RADICE / "scripts" / "misura-ui.py"


class BloccaTest(unittest.TestCase):
    def setUp(self):
        self.cartella = Path(tempfile.mkdtemp())
        for modulo in ("mobile", "wear"):
            for parte in ("res", "java"):
                shutil.copytree(RADICE / modulo / "src/main" / parte, self.cartella / modulo / "src/main" / parte)

    def tearDown(self):
        shutil.rmtree(self.cartella)

    def misura(self):
        ambiente = dict(os.environ, MISURA_UI_RADICE=str(self.cartella))
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--blocca"],
            env=ambiente,
            capture_output=True,
            text=True,
        )

    def test_i_sorgenti_di_oggi_passano(self):
        esito = self.misura()
        self.assertEqual(0, esito.returncode, esito.stderr)

    def test_un_colore_scritto_a_mano_blocca(self):
        layout = self.cartella / "mobile/src/main/res/layout/prova_della_misura.xml"
        layout.write_text(
            '<View xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:layout_width="match_parent"\n'
            '    android:layout_height="match_parent"\n'
            '    android:background="#FF0000" />\n',
            encoding="utf-8",
        )
        esito = self.misura()
        self.assertEqual(1, esito.returncode)
        self.assertIn("Colori scritti a mano, telefono", esito.stderr)
        self.assertIn("prova_della_misura.xml", esito.stderr)

    def test_un_quadrante_fuori_misura_blocca(self):
        misure = self.cartella / "wear/build/misura"
        misure.mkdir(parents=True)
        (misure / "geometria-tondo-192.txt").write_text("- Tondo da 192dp: 1 fuori misura su 137 misure\n", encoding="utf-8")
        esito = self.misura()
        self.assertEqual(1, esito.returncode)
        self.assertIn("1 fuori misura", esito.stderr)


if __name__ == "__main__":
    unittest.main()
