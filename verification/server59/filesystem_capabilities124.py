"""Fixture creation only. Never suppress a product operation or a failed assertion."""
import os
import unittest


def fixture_symlink(link, target, **kwargs):
    try:
        link.symlink_to(target, **kwargs)
    except OSError as error:
        if os.name == 'nt' and getattr(error, 'winerror', None) == 1314:
            raise unittest.SkipTest('Windows denied creation of the symlink fixture (1314); product checks were not executed') from error
        raise
