import json
import os
import urllib.error
import urllib.request
from urllib.parse import urlparse

MODELS = ("llama3.1:8b", "gemma3:4b")
CLASSES = {
    "TRANSIENT_INFRASTRUCTURE": "REPROCESS",
    "SCHEMA_MISMATCH": "ESCALATE",
    "VALIDATION_ERROR": "ESCALATE",
    "PERMANENT_BUSINESS_ERROR": "ESCALATE",
    "UNKNOWN": "ESCALATE",
}
CLASS_GUIDANCE = {
    "TRANSIENT_INFRASTRUCTURE": (
        "Include an exception or message pattern such as SocketTimeoutException, "
        "ConnectException, TimeoutException, connection reset, timed out, or "
        "temporarily unavailable. Do not include schema, validation, or business-rule signals."
    ),
    "SCHEMA_MISMATCH": (
        "Include an exception or message pattern such as SchemaMismatchException, "
        "JsonMappingException, SerializationException, unknown field, or "
        "failed to deserialize. Do not include transient or business-rule signals."
    ),
    "VALIDATION_ERROR": (
        "Include an exception or message pattern such as ConstraintViolationException, "
        "MethodArgumentNotValidException, validation failed, or required field. "
        "Do not include schema, transient, or business-rule signals."
    ),
    "PERMANENT_BUSINESS_ERROR": (
        "Include an exception or message pattern such as InsufficientFundsException, "
        "OrderNotFoundException, insufficient funds, order not found, or payment declined. "
        "Do not include transient, schema, or validation signals."
    ),
    "UNKNOWN": (
        "Use an unfamiliar synthetic exception and generic message that do not match "
        "transient, schema, validation, or business-rule patterns."
    ),
}
OUTPUT = os.path.join("evals", "dataset", "baseline.jsonl")
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
CASES_PER_MODEL_CLASS = 10
MAX_CASES_PER_REQUEST = 5
MAX_RETRIES = 4


def generate(model, failure_class, count):
    cases = []
    attempts = MAX_RETRIES * (
        (count + MAX_CASES_PER_REQUEST - 1) // MAX_CASES_PER_REQUEST
    )
    for attempt in range(attempts):
        remaining = count - len(cases)
        if not remaining:
            break
        requested = min(remaining, MAX_CASES_PER_REQUEST)
        prompt = f"""Create exactly {requested} distinct synthetic DLQ failure examples.
The expected class for every example MUST be {failure_class}; the correct action
MUST be {CLASSES[failure_class]}. {CLASS_GUIDANCE[failure_class]}
Include errorClass, errorMessage, and
stackTrace with realistic but synthetic values. Keep each field short and limit
stackTrace to at most two synthetic exception frames. Return only a JSON object
with a `cases` array. Each case must have `errorClass`, `errorMessage`,
`stackTrace`.
Do not include real names, payloads, secrets, or commentary."""
        request = urllib.request.Request(
            f"{OLLAMA_URL}/api/generate",
            data=json.dumps(
                {
                    "model": model,
                    "prompt": prompt,
                    "stream": False,
                    "format": "json",
                    "options": {"num_predict": 8192, "temperature": 0.2},
                }
            ).encode(),
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=600) as response:
                result = json.load(response)
        except (TimeoutError, urllib.error.URLError) as error:
            raise RuntimeError(
                f"Local Ollama generation failed for {model}: {error}"
            ) from error
        try:
            generated = json.loads(result["response"])["cases"]
        except json.JSONDecodeError as error:
            if attempt < attempts - 1:
                continue
            raise RuntimeError(
                f"Invalid JSON response from {model} after retries: {error}"
            ) from error
        except (KeyError, TypeError) as error:
            raise RuntimeError(f"Invalid JSON response from {model}: {error}") from error
        if not isinstance(generated, list):
            raise RuntimeError(f"{model} response has no cases array")
        for case in generated:
            if not isinstance(case, dict):
                continue
            error_class = case.get("errorClass", case.get("error_class"))
            error_message = case.get("errorMessage", case.get("error_message"))
            stack_trace = case.get("stackTrace", case.get("stack_trace"))
            if not isinstance(error_class, str):
                continue
            if error_message is not None and not isinstance(error_message, str):
                continue
            if stack_trace is not None and not isinstance(stack_trace, str):
                continue
            cases.append(
                {
                    "errorClass": error_class,
                    "errorMessage": error_message,
                    "stackTrace": stack_trace,
                }
            )
            if len(cases) == count:
                break
    if len(cases) != count:
        raise RuntimeError(
            f"{model} generated {len(cases)} cases after {attempts} attempts; "
            f"expected {count}"
        )
    return cases


def write_cases(cases):
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as dataset:
        for case in cases:
            dataset.write(json.dumps(case, ensure_ascii=False) + "\n")


def main():
    if urlparse(OLLAMA_URL).hostname not in {"localhost", "127.0.0.1", "::1"}:
        raise RuntimeError("Dataset generation only supports a local Ollama server")
    cases = []
    if os.path.exists(OUTPUT):
        with open(OUTPUT, encoding="utf-8") as dataset:
            cases = [json.loads(line) for line in dataset if line.strip()]
    for model in MODELS:
        for failure_class in CLASSES:
            existing = [
                case
                for case in cases
                if case["generator"] == model
                and case["expectedClass"] == failure_class
            ]
            missing = CASES_PER_MODEL_CLASS - len(existing)
            if missing <= 0:
                continue
            generated = generate(model, failure_class, missing)
            for case in generated:
                number = len(cases) + 1
                cases.append(
                    {
                        "id": f"case-{number:03}",
                        "errorClass": case["errorClass"],
                        "errorMessage": case["errorMessage"],
                        "stackTrace": case["stackTrace"],
                        "expectedClass": failure_class,
                        "correctAction": CLASSES[failure_class],
                        "generator": model,
                        "reviewed": False,
                        "split": "held-out" if number % 5 == 0 else "train",
                    }
                )
            write_cases(cases)
            print(f"Generated {len(cases)} cases ({model}, {failure_class})")
    print(f"Wrote {len(cases)} generated cases to {OUTPUT}")


if __name__ == "__main__":
    main()
