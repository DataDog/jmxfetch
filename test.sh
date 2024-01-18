#!/bin/zsh

# Define a signal handler
trap 'echo "SIGINT received, exiting..."; exit' SIGINT

# Create a new subfolder with a datetime-based name
datetime=$(date +"%Y%m%d%H%M%S")
archive_folder="./logs/archive_${datetime}"
mkdir -p "$archive_folder"

# Move any existing .txt files into the new subfolder
mv ./logs/*.txt "$archive_folder" 2>/dev/null

mkdir -p "./logs"

num_runs=${1:-40}
for counter in $(seq 1 $num_runs); do
  ./mvnw test -Dtest=TestReconnectContainer#testJMXDirectReconnect > "./logs/temp_output_${counter}.txt"
  exit_code=$?
  mv "./logs/temp_output_${counter}.txt" "./logs/output_${counter}_exit${exit_code}.txt"

  # Print the current timestamp and the iteration counter value
  echo -n "$(date +"%Y-%m-%d %H:%M:%S") - Iteration: ${counter}"

  # Print an excerpt of the results line
  echo -ne "\t"; grep 'Time elapsed:' "./logs/output_${counter}_exit${exit_code}.txt"

  if [ $exit_code -ne 0 ]; then
    echo "Test failed after ${counter} iterations with exit code ${exit_code}. Exiting..."
    exit $exit_code
  fi
done
echo "Ran ${num_runs} times, no failures found"

