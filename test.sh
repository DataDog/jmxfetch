#!/bin/zsh

# Define a signal handler
trap 'echo "SIGINT received, exiting..."; exit' SIGINT

mkdir -p "./logs"


num_runs=${1:-40}
for counter in $(seq 1 $num_runs); do
  ./mvnw test -Dtest=TestReconnectContainer#testJMXDirectReconnect > "./logs/temp_output_${counter}.txt"
  exit_code=$?
  mv "./logs/temp_output_${counter}.txt" "./logs/output_${counter}_exit${exit_code}.txt"
  if [ $exit_code -ne 0 ]; then
    echo "Test failed after ${counter} iterations with exit code ${exit_code}. Exiting..."
    exit $exit_code
  fi
done
echo "Ran ${num_runs} times, no failures found"

