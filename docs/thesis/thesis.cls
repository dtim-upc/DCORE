\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{thesis}[]

\RequirePackage{ifthen}

\newcommand\bhrule{\typeout{------------------------------------------------------------------------------}}
\newcommand\btypeout[1]{\bhrule\typeout{\space #1}\bhrule}

% Defaults / Base commands for options
\newcommand*{\@dateprefix}{}
\newcommand*{\selectedlanguage}{}
\newcommand*{\selectedtype}{}
\newboolean{isThesis}
\setboolean{isThesis}{false}

% Declaring Options
\DeclareOption{draft}{%
  \renewcommand*{\@dateprefix}{\relax }%
  \PassOptionsToClass{draft}{report}%
}
\DeclareOption{final}{%
  \renewcommand*{\@dateprefix}{\relax}%
  \PassOptionsToClass{final}{report}%
}
\DeclareOption{thesis}{%
  \renewcommand*{\selectedtype}{thesis}%
  \setboolean{isThesis}{true}
}
\ExecuteOptions{final,thesis}
\ProcessOptions\relax

\PassOptionsToPackage{final}{hyperref}% Always use hyperlinks

% Load base class 

% For onesided printing uncomment next command
\LoadClass[12pt,a4paper,oneside]{report}

% Load required Packages
\RequirePackage{fancyhdr}
\RequirePackage[OT1]{fontenc}

% Define commands for title page

\newcommand{\subtitle}[1]{\renewcommand{\@subtitle}{#1}}
\newcommand{\@subtitle}{\relax}
\newcommand*{\setthesistype}[1]{\renewcommand*{\thesistype}{#1}}
\newcommand*{\thesistype}{\@latex@warning@no@line{No \protect\thesistype\space given}Enter the thesis type!}
\newcommand*{\thesistitle}{\@latex@warning@no@line{No \protect\thesistitle\space given}Enter Bachelor Thesis or Master Thesis!}
\newcommand*{\degree}[1]{\renewcommand*{\@degree}{#1}}
\newcommand*{\@degree}{\@latex@warning@no@line{No \protect\semester\space given}Enter the degree!}
\newcommand*{\supervisor}[1]{\renewcommand*{\@supervisor}{#1}}
\newcommand*{\@supervisor}{\@latex@warning@no@line{No \protect\supervisor\space given}Enter the supervisor!}

\newcommand*{\@@date}{\@dateprefix\@date}

\renewcommand\maketitle{\newpage%
  \let\oldand\and
  \thispagestyle{empty}
  \setcounter{page}\@ne
  \let\footnotesize\small
  \let\footnoterule\relax
  \let \footnote \thanks
  \null\vfil
  \begin{center}%
    \smallskip
    \hrule % Horizontal line
    {\vspace*{0.5cm}\huge \bf \@title \par}
    \bigskip
    \hrule % Horizontal line
    \smallskip
  {\Large \textsc{\thesistype} \textsc{Thesis} }%
  \vskip 1em%
  {\Large \textsc{\@degree} \par}
  \vskip 0.5em%
  \Large
    \vskip 0.5em%
    {\Large \@author \par}% 
    \vskip 1em%
    {\large \@@date \par}%       % Set date in \large size.
  \vfil
  % \begin{center}
  %   \includegraphics[width=0.7\textwidth]{figures/upc_logo.png}
  % \end{center}
  {\normalsize FACULTAT D’INFORMÀTICA DE BARCELONA (FIB)\\}
  {\normalsize UNIVERSITAT POLITÈCNICA DE CATALUNYA (UPC) – BarcelonaTech}
  \vskip 0.1em%
  {\normalsize Advisors: \@supervisor}
  \end{center}
  \newpage
  \thispagestyle{empty}
  \vfil
  \setcounter{page}\@ne
  \setcounter{footnote}{0}%
  \pagenumbering{roman}
  \global\let\maketitle\relax
  \global\let\@author\@empty
  \global\let\@date\@empty
  \global\let\@title\@empty
  \global\let\title\relax
  \global\let\author\relax
  \global\let\date\relax
  \global\let\and\relax
  \let\and\oldand
}


\thispagestyle{plain}
\usepackage{setspace}
\onehalfspacing
\setlength{\parindent}{0pt}
\setlength{\parskip}{2.0ex plus0.5ex minus0.2ex}
\usepackage{vmargin}
\setmarginsrb           { 1.5in}  % left margin
                        { 0.6in}  % top margin
                        { 1.3in}  % right margin
                        { 1.0in}  % bottom margin
                        {  20pt}  % head height
                        {0.25in}  % head sep
                        {   9pt}  % foot height
                        { 0.3in}  % foot sep
\raggedbottom
\setlength{\topskip}{1\topskip \@plus 5\p@}
\doublehyphendemerits=10000       % No consecutive line hyphens.
\brokenpenalty=10000              % No broken words across columns/pages.
\widowpenalty=10000                % Almost no widows at bottom of page.
\clubpenalty=10000                 % Almost no orphans at top of page.
\interfootnotelinepenalty=9999    % Almost never break footnotes.
\usepackage{fancyhdr}
\lhead[\rm\thepage]{\fancyplain{}{\sl{\rightmark}}}
\rhead[\fancyplain{}{\sl{\leftmark}}]{\rm\thepage}
\chead{}\lfoot{}\rfoot{}\cfoot{}
\pagestyle{fancy}
\renewcommand{\chaptermark}[1]{\btypeout{\thechapter\space #1}\markboth{\@chapapp\ \thechapter\ #1}{\@chapapp\ \thechapter\ #1}}
\renewcommand{\sectionmark}[1]{}
\renewcommand{\subsectionmark}[1]{}

\usepackage{amsmath,amsfonts,amssymb,amscd,amsthm,xspace}
\theoremstyle{plain}
\newtheorem{example}{Example}[chapter]
\newtheorem{complexity}{Complexity}[chapter]
\newtheorem*{theorem*}{Theorem}
\newtheorem{theorem}{Theorem}[chapter]
\newtheorem{corollary}[theorem]{Corollary}
\newtheorem{lemma}[theorem]{Lemma}
\newtheorem{proposition}[theorem]{Proposition}
\newtheorem{axiom}[theorem]{Axiom}
\theoremstyle{definition}
\newtheorem{definition}[theorem]{Definition}
\theoremstyle{remark}
\newtheorem{remark}[theorem]{Remark}
\usepackage[centerlast,small,sc]{caption}
\setlength{\captionmargin}{20pt}
\newcommand{\fref}[1]{Figure~\ref{#1}}
\newcommand{\tref}[1]{Table~\ref{#1}}
\newcommand{\eref}[1]{Equation~\ref{#1}}
\newcommand{\cref}[1]{Chapter~\ref{#1}}
\newcommand{\sref}[1]{Section~\ref{#1}}
\newcommand{\lref}[1]{Listing~\ref{#1}}
\newcommand{\dref}[1]{Definition~\ref{#1}}
\newcommand{\pref}[1]{Proposition~\ref{#1}}
\newcommand{\aref}[1]{Algorithm~\ref{#1}}
\newcommand{\apref}[1]{Appendix~\ref{#1}}
\renewcommand{\topfraction}{0.85}
\renewcommand{\bottomfraction}{.85}
\renewcommand{\textfraction}{0.1}
\renewcommand{\dbltopfraction}{.85}
\renewcommand{\floatpagefraction}{0.75}
\renewcommand{\dblfloatpagefraction}{.75}
\setcounter{topnumber}{9}
\setcounter{bottomnumber}{9}
\setcounter{totalnumber}{20}
\setcounter{dbltopnumber}{9}


% Define structuring commands
\newcommand{\mainmatter}{%
  \cleardoublepage%
  \pagenumbering{arabic}%
  \setcounter{page}{1}%
}

\newcommand\acknowledgements[1]{
\cleardoublepage
\thispagestyle{plain}
% \setlength{\parindent}{20pt}
% \setlength{\parskip}{0pt}
\begin{center}{\huge{\textit{Acknowledgements}} \par}\end{center}
\vspace{20pt}
{\normalsize #1}
\vfil\vfil\null
% \setlength{\parindent}{0pt}
% \setlength{\parskip}{2.0ex plus0.5ex minus0.2ex}
}

\newenvironment{pagecentered}{%
\vspace*{\stretch{2}}%
\begin{center}%
\begin{minipage}{.8\textwidth}%
}{%
\end{minipage}%
\end{center}%
\vspace*{\stretch{3}}\clearpage}

\newcommand{\license}{%
  \thispagestyle{empty}
  \begin{pagecentered}

  \doublespacing

  DISTRIBUTED COMPLEX EVENT RECOGNITION \\
  COPYRIGHT \\
  2022 \\
  Arnau Abella \bigskip

  \singlespacing

  This work is licensed under a \href{https://www.gnu.org/licenses/gpl-3.0.en.html}{GNU General Public License v3.0}. To view a copy of this license, visit \bigskip

  \url{https://www.gnu.org/licenses/gpl-3.0.en.html} \bigskip

  The complete source code for this document is available from \bigskip

  \url{https://github.com/dtim-upc/DCORE}
  \end{pagecentered}
}
