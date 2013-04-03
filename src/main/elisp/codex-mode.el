;; codex.el --- integrates Codex into Emacs via a minor-mode.
;;
;; Copyright (C) 2013 Michael Bayne
;;
;; Author: Michael Bayne <mdb * samskivert com>
;; Version: 1.0
;; URL: http://github.com/samskivert/codex
;; Compatibility: GNU Emacs 23.x, GNU Emacs 24.x
;;
;; This file is NOT part of GNU Emacs.
;;
;;; Commentary:
;;
;; Configure Codex for use in any coding mode by adding the following to your .emacs file
;; (customizing key bindings to your preference, naturally):
;;
;; (load "path/to/codex")
;; (require `codex)
;; (mapc
;;  (lambda (lang-hook)
;;    (add-hook lang-hook 'codex-mode))
;;  '(java-mode-hook
;;    scala-mode-hook
;;    etc.))
;;
;;; Code:

(require 'url)

(defvar codex-url "http://localhost:3003/"
  "The URL via which we communicate with Codex.")
(defvar codex-marker-ring (make-ring 16)
  "Ring of markers which are locations from which \\[codex-open-symbol] was invoked.")

;; Used to handle next- and previous-error when navigating through results
(defvar codex-error-pos nil)
(make-variable-buffer-local 'codex-error-pos)

;; Used when querying Codex and processing the results
(defvar codex-buffer-name "*codex*")
(defvar codex-searched-sym nil)

(defun codex-browse-url (url)
  "The function called by the Codex bindings to display a URL.
The default implementation simply calls (browse-url url) but this
can be redefined to provide custom behavior."
  (browse-url url)
  )

(defun codex-doc-symbol (name)
  "Displays the documentation for the supplied symbol. Results
are displayed in your web browser."
  (interactive (list (read-from-minibuffer "Symbol: " (thing-at-point 'symbol))))
  (codex-browse-url (concat codex-url "docs/" name))
  )

(defun codex-open-symbol (name)
  "Navigates to the definition of the symbol under the point. If
Codex is not able to uniquely identify the symbol under the
point, it will return all symbols with the same name as the
queried symbol. These matches can be navigated using
\\[next-error] and \\[previous-error]]."
  (interactive (list (read-from-minibuffer "Symbol: " (thing-at-point 'symbol))))
  (let ((url-request-method "POST")
        (url-request-data (concat (buffer-file-name) "\n" (number-to-string (- (point) 1))))
        ;; (buffer (get-buffer-create codex-buffer-name))
        )
    (setq codex-searched-sym name)
    (url-retrieve (concat codex-url "query/find/" name) 'codex-handle-query-result)
    ))

(defun pop-codex-mark ()
  "Pop back to where \\[codex-open-symbol] was last invoked."
  (interactive)
  (if (ring-empty-p codex-marker-ring)
      (error "No previous locations for codex-open-symbol invocation."))
  (let ((marker (ring-remove codex-marker-ring 0)))
    (switch-to-buffer (or (marker-buffer marker)
                          (error "The marked buffer has been deleted.")))
    (goto-char (marker-position marker))
    (set-marker marker nil nil)))

(define-minor-mode codex-mode
  "Toggle Codex mode.
Interactively with no argument, this command toggles the mode. A
positive prefix argument enables the mode, any other prefix
argument disables it. From Lisp, argument omitted or nil enables
the mode, `toggle' toggles the state.

When Codex mode is enabled, key bindings are enabled that make
requests of the Codex daemon allowing one to, for example,
navigate to a definition or insert an import for a class."
  ;; The initial value.
  :init-value nil
  ;; The indicator for the mode line.
  :lighter " Codex"
  ;; The minor mode bindings.
  :keymap '(("\C-c\C-j" . codex-doc-symbol)
            ("\C-c\C-k" . codex-open-symbol)
            ("\M-."     . codex-open-symbol)
            ("\M-/"     . pop-codex-mark)
            ("\M-]"     . next-error)
            ("\M-["     . previous-error))
  :group 'codex)

;;
;; implementation details

(define-derived-mode codex-results-mode nil "codex"
  "Major mode for Codex search results."
  (setq next-error-function 'codex-next-error-function codex-error-pos nil))

(defun codex-handle-query-result (status)
  (let ((codex-buffer (get-buffer-create codex-buffer-name))
        (buffer (current-buffer))
        (bpos (search-forward "\n\n")))
    (if (eq (car status) :error)
        (let ((body (buffer-substring bpos (point-max))))
          (message (concat "Codex: " body)))
      (progn
        ;; (switch-to-buffer (current-buffer))
        (copy-to-buffer codex-buffer bpos (point-max))
        (kill-buffer buffer)
        (setq next-error-last-buffer codex-buffer)
        (let ((rcount (with-current-buffer codex-buffer
                        (codex-results-mode)
                        (goto-char 0)
                        (count-lines (point-min) (point-max))
                        )))
          (message (format "Codex found %d result(s)." rcount))
          (codex-next-error-function 0 nil))
        )
      )
    )
  )

(defun codex-next-error-function (arg reset)
  (let ((savepoint (point-marker)))
    (with-current-buffer (get-buffer codex-buffer-name)
      ;; position the point on the desired result (in the codex results buffer)
      (if reset
          ;; if we're resetting, just go to the first result
          (goto-char 0)
        ;; otherwise we'll be moving foward or backward based on the value of arg
        (progn
          ;; handle wrapping back to the end if we're at the first result and arg < 0
          (when (and (eq 1 (line-number-at-pos)) (< arg 0))
            (message "Start of matches (wrapped).")
            ;; move to the end of the buffer; below we will back up one line
            ;; and end up at the right place
            (goto-char (point-max)))
          ;; now move forward (or backward) the requested number of results (lines)
          (forward-line arg)
          ;; if we ended up on the last line (which is blank) then wrap back
          ;; around to the first result
          (when (null (thing-at-point 'symbol))
            (message "End of matches (wrapped).")
            (goto-char 0))))
      ;; now process the result on the current line
      (let* ((line (thing-at-point 'line))
             (toks (split-string line)))
        (cond ((string= (car toks) "nomatch")
               (message "Could not locate symbol: %s" codex-searched-sym))
              ((string= (car toks) "match")
               (ring-insert codex-marker-ring savepoint) ;; record whence we came
               ;; TODO: use regex to strip off "match" and lineno, everything after that is
               ;; the filename and may contain spaces
               (codex-visit-file (caddr toks) (string-to-number (cadr toks)))
               )
              (t (message (concat "Failed to parse: " (substring line 0 -1)))) ;; strip newline
              )))))

(defun codex-visit-file (path pos)
  ;; if the path is of the form file!path then we need to do some special handling to navigate into
  ;; an archive file and open the path in question
  (let* ((comps (split-string path "!"))
         (clen (length comps)))
    (cond ((eq clen 1)
           (find-file path))
          (t
           (find-file (car comps))
           (search-forward (cadr comps))
           (archive-extract))))
  ;; now we move to the line in question
  (goto-char (point-min))
  (forward-line (1- (+ pos 1)))
  ;; TODO: change back to this when codex uses char offsets
  ;; (goto-char (+ pos 1))
  )

(provide 'codex-mode)
;;; codex.el ends here
