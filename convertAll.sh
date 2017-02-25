#!/bin/bash

## This assumes that language-specific directories with the files from the UP corpus for
## that language as well as output directories with the same name and suffix "_gate" are
## present or linked inside this directory.

# list of input directories to process
inDirs="UD_English UD_French UD_German UD_Spanish UD_Spanish-AnCora"

rm convertAll.log
for indir in $inDirs
do
  outdir=${indir}_gate
  shopt -s nullglob   # avoid file pattern to be used if no matching file exists
  for infile in $indir/*.conllu
  do
    echo RUNNING ./convert.sh $infile $outdir
    ./convert.sh $infile $outdir 2>&1 | tee -a convertAll.log
  done
  shopt -u nullglob  # go back to original setting
done
