"""Explicit fixture approval; never imported by production code."""
def stage(changes,updates,remove=()):
 return changes.stage(updates,remove,approval=changes.approve(changes.review(updates,remove)))
