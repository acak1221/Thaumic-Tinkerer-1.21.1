# SPDX-License-Identifier: MIT
import tempfile
from pathlib import Path
import unittest

from release_bundle import test_summary


class ReleaseReportTests(unittest.TestCase):
    def test_missing_or_empty_report_cannot_publish(self):
        with self.assertRaises(ValueError):
            test_summary([])
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'report.xml'
            path.write_text('<testsuite tests="99"/>')
            with self.assertRaises(ValueError):
                test_summary([path])

    def test_failed_error_or_skipped_test_blocks_publication(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'report.xml'
            for problem in ('failure', 'error', 'skipped'):
                with self.subTest(problem=problem):
                    path.write_text(f'<testsuite><testcase><{problem}/></testcase></testsuite>')
                    with self.assertRaises(ValueError):
                        test_summary([path])

    def test_count_actual_cases_across_suites(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'report.xml'
            path.write_text('<testsuites><testsuite tests="99"><testcase/><testcase/></testsuite></testsuites>')
            self.assertEqual(test_summary([path]), dict(passed=2, failed=0, errors=0, skipped=0))


if __name__ == '__main__':
    unittest.main()
