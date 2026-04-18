#!/bin/bash
git add .
read -p "Описание: " msg
git commit -m "$msg"
git push
