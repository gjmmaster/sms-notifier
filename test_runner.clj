(require 'sms-notifier.core-test)
(require 'clojure.test)

(let [summary (clojure.test/run-tests 'sms-notifier.core-test)]
  (System/exit (if (and (zero? (:fail summary)) (zero? (:error summary))) 0 1)))
