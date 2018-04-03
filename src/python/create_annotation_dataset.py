from pathlib import Path
import pandas as pd
import numpy as np
import csv
import string

import sys
from stop_words import get_stop_words
stop_words = get_stop_words('ru')
import pickle
from collections import defaultdict
from pymystem3 import Mystem
from sklearn.feature_extraction.text import TfidfVectorizer

_mystem = Mystem()


def lemmatize(s, tries=0):
    global _mystem
    try:
        return _mystem.lemmatize(s)
    except BrokenPipeError:
        if tries > 10:
            raise
        _mystem = Mystem()
        return lemmatize(s, tries+1)


with open('resources/ru_wikionary_senses.pkl', 'rb') as f:
    abbr2senses = pickle.load(f)

_lemmatized_senses = {}

sense2abbr = defaultdict(list)
for abbr, senses in abbr2senses.items():
    for s in senses:
        l = ''.join(_mystem.lemmatize(s)).strip()
        sense2abbr[l].append(abbr)

for sense, abbrs in sense2abbr.items():
    main_abbr = abbrs[0]
    _lemmatized_senses[sense] = main_abbr
    for a in abbrs[1:]:
        _lemmatized_senses[a] = main_abbr


def desynonimize(text, synset=None):
    """ Lemmatize and desynonimize text
    """

    if synset is None:
        synset = _lemmatized_senses

    lemmas = lemmatize(text)
    lemtext = ''.join(lemmas).strip()

    for s in synset:
        if s in lemtext:
            lemtext = lemtext.replace(s, synset[s])
    return lemtext


_translator = str.maketrans('', '', string.punctuation)


def lemmatizate_synonimize_sentence(sentence):
    proc_sentence = desynonimize(sentence)
    proc_sentence = ' '.join([w for w in proc_sentence.translate(_translator).lower().split(' ')
                              if w not in stop_words])
    proc_sentence = proc_sentence.replace('университет', 'вуз')
    proc_sentence = proc_sentence.replace('универ', 'вуз')
    return proc_sentence


def main():
    data_folder = Path('~/data/EUS').expanduser()
    answers = data_folder / 'classes.list'
    questions = data_folder / 'zpp_questions.example.csv'

    with answers.open() as f:
        answers = [line.strip() for line in f]

    vectorizer = TfidfVectorizer()
    a_tfidf = vectorizer.fit_transform([lemmatizate_synonimize_sentence(a) for a in answers])
    print('Answers have been vectorized', file=sys.stderr)

    annotation_dataset = Path('classification_zpp_input.csv')

    questions = sorted(set(pd.read_csv(questions)['Название']))
    q_tfidf = vectorizer.transform([lemmatizate_synonimize_sentence(q) for q in questions])
    print('Questions have been vectorized', file=sys.stderr)

    distances = a_tfidf.dot(q_tfidf.T)

    print(f'Writing to {annotation_dataset}', file=sys.stderr)
    with annotation_dataset.open('w') as f:
        cw = csv.writer(f)
        for i, q in enumerate(questions):
            order = np.argsort(distances[:, i].toarray()[:, 0])
            cw.writerow([q, '|'.join(answers[a].replace('|', '') for a in order[::-1])])


if __name__ == '__main__':
    main()