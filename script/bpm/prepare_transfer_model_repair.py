#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prepare an offline transfer-model update; never publish or modify an instance."""
import argparse
import copy
import json
from pathlib import Path


def prepare(exported, model_id):
    if not isinstance(exported, dict) or not model_id:
        raise ValueError('Model export and explicit model ID are required')
    if 'code' in exported:
        if exported['code'] != 0:
            raise ValueError('Model export API did not succeed')
        exported = exported.get('data')
    if not isinstance(exported, dict):
        raise ValueError('Missing model export')
    if exported.get('key') != 'zsjos_lead_transfer_request' or exported.get('type') != 20:
        raise ValueError('Expected the existing SIMPLE transfer model')
    if exported.get('id') is not None and str(exported['id']) != model_id:
        raise ValueError('Model ID differs from export')
    result = copy.deepcopy(exported)
    start = result.get('simpleModel')
    if not isinstance(start, dict) or start.get('id') != 'StartUserNode' or start.get('type') != 10:
        raise ValueError('Unexpected starter topology')
    review = start.get('childNode')
    if not isinstance(review, dict) or review.get('id') != 'ownerManagerReview' or review.get('type') != 11:
        raise ValueError('Expected the review directly after the starter')
    end = review.get('childNode')
    if not isinstance(end, dict) or end.get('type') != 1 or end.get('childNode') is not None:
        raise ValueError('Unexpected review successor')
    if any(node.get('conditionNodes') for node in (start, review, end)):
        raise ValueError('Conditional topology requires separate review')
    strategy, param = review.get('candidateStrategy'), review.get('candidateParam')
    if (strategy, param) != (60, '${coll_userList}') and not (strategy == 35 and param in (None, '')):
        raise ValueError('Unrecognized reviewer strategy; no correction prepared')
    # Preserve tenant-managed settings and the entire node tree. Replacing it with
    # the template would discard local buttons, approval rules and form settings.
    review['candidateStrategy'] = 35
    review['candidateParam'] = None
    result['id'] = model_id
    result['bpmnXml'] = None
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export', required=True, type=Path)
    parser.add_argument('--model-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = prepare(json.loads(args.export.read_text(encoding='utf-8-sig')), args.model_id)
    # An existing review artifact must not be silently overwritten.
    with args.output.open('x', encoding='utf-8', newline='\n') as output:
        output.write(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main()

