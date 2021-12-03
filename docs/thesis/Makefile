DOC=report
BIBFILE=bibliography
PDF=${DOC}.pdf
BIB=${BIBFILE}.bib

LATEX = pdflatex -halt-on-error -shell-escape -synctex=1
BIBTEX = bibtex

.PHONY : all pdf clean cleanpdf cleandist

# ----------------------------------------------------------

all: pdf

pdf: ${PDF}

${PDF}: clean $(wildcard *.tex) $(wildcard *.bib)
	${LATEX}  ${DOC} # document with ? on references
	${BIBTEX} ${DOC}
	${LATEX}  ${DOC} # document with references
	${LATEX}  ${DOC} # sometimes references break page numbering

# ----------------------------------------------------------

cleandist: clean cleanpdf cleantikzcache

clean:
	rm -f *.eps
	rm -f *.aux *.log *.out *.bbl *.blg *~ *.bak *.ps
	rm -f *.brf *.lof *.lot *.toc *.glo *.ist *.fdb_latexmk *.fls
	rm -rf _minted-* .auctex-auto
	rm -rf *.auxlock *.synctex.gz

cleanpdf:
	rm -f ${PDF}

cleantikzcache:
	rm -rf tikz/cache/*
