.PHONY : all report.pdf clean cleanpdf cleandist

TARGET=report

all: report.pdf

# latexmk -pdf -latexoption="-shell-escape" report.tex

report.pdf: clean $(wildcard *.tex) $(wildcard *.bib)
	- pdflatex -halt-on-error -shell-escape $(TARGET) # document with ? on references
	- bibtex  $(TARGET) # references
# TODO uncomment
# - pdflatex -halt-on-error -shell-escape $(TARGET) # document with references
	pdflatex -halt-on-error -shell-escape $(TARGET) # sometimes references break page numbering

clean:
	rm -f *.eps
	rm -f *.aux *.log *.out *.bbl *.blg *~ *.bak $(TARGET).ps
	rm -f *.brf *.lof *.lot *.toc *.glo *.ist *.fdb_latexmk *.fls
	rm -rf _minted-$(TARGET) .auctex-auto

cleanpdf:
	rm -f $(TARGET).pdf

cleandist: clean cleanpdf
