# Krill Python Lambda Scripts

This directory contains Python scripts that can be executed by Krill's Lambda executor as part of your automation workflows.

Learn more about using Lambda scripts in Krill in the [official documentation](https://krillswarm.com/posts/2026/01/25/lambda-executor).

## Overview

Lambda scripts act as data transformation functions in your Krill automation pipeline:
- **Input:** Data from a source DataPoint (sensor, API, etc.)
- **Processing:** Your custom Python logic
- **Output:** Result stored in a target DataSource

## Security

All scripts in this directory run with the following security measures:

### Automatic Sandboxing
Krill automatically detects and uses available sandboxing:
1. **Firejail** (preferred) - Lightweight Linux namespace isolation
2. **Docker** (fallback) - Container-based isolation  
3. **Direct execution** (last resort) - With logging warnings

### Resource Limits
- **Memory:** 256MB maximum
- **Execution time:** 30 second timeout
- **Filesystem:** Read-only access to `/opt/krill/lambdas` only
- **User:** Scripts run as unprivileged `krill` user

### Recommended: Install Firejail
For best security, install Firejail:
```bash
sudo apt-get update
sudo apt-get install firejail
sudo systemctl restart krill
```

## Example Scripts

### KrillPythonLambdaBasic.py
Simple echo script that passes input through unchanged. Use as a template.

### TemperatureConverter.py
Converts Celsius temperature to Fahrenheit. Demonstrates numeric processing.

### sht30.py
Parses sensor data from SHT30 temperature/humidity sensor, computes averages.

## Creating Your Own Scripts

### 1. Create Script File

```bash
sudo nano /opt/krill/lambdas/my_script.py
```

### 2. Use This Template

```python
#!/usr/bin/env python3
"""
Script name and description
"""

import sys

def main():
    # Validate input
    if len(sys.argv) < 2:
        print("ERROR: No input provided", file=sys.stderr)
        sys.exit(1)
    
    try:
        # Get input
        input_value = sys.argv[1]
        
        # Process (your logic here)
        result = process(input_value)
        
        # Output result
        print(result)
        
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

def process(value):
    # Your custom logic
    return value

if __name__ == "__main__":
    main()
```

### 3. Set Permissions

```bash
sudo chown krill:krill /opt/krill/lambdas/my_script.py
sudo chmod 755 /opt/krill/lambdas/my_script.py
```

### 4. Test Script

```bash
# Basic test
sudo -u krill python3 /opt/krill/lambdas/my_script.py "test input"

# Test in sandbox (if firejail installed)
sudo -u krill firejail \
  --noprofile \
  --whitelist=/opt/krill/lambdas \
  --read-only=/opt/krill/lambdas \
  --private \
  --private-tmp \
  python3 /opt/krill/lambdas/my_script.py "test input"
```

## Best Practices

### ✅ DO:
- Keep scripts simple and focused
- Validate all inputs thoroughly
- Handle errors gracefully with try-catch
- Write results to stdout, errors to stderr
- Test manually before deploying in Krill
- Document what your script does

### ❌ DON'T:
- Import unnecessary external libraries
- Attempt to write/modify files
- Spawn subprocesses or fork
- Assume unlimited execution time
- Store sensitive credentials in scripts
- Make assumptions about the environment

## Input/Output Format

### Input
Scripts receive input as the first command-line argument:
```python
input_value = sys.argv[1]
```

The input is typically:
- A numeric value (e.g., `"23.5"`)
- A string value (e.g., `"ON"`)
- JSON data (e.g., `'{"temp": 23.5}'`)

### Output
Scripts write output to stdout:
```python
print(result)
```

Krill captures stdout and stores it in the target DataSource.

### Errors
Write errors to stderr and exit with non-zero code:
```python
print("ERROR: Something went wrong", file=sys.stderr)
sys.exit(1)
```

## Configuration in Krill

1. **Create Lambda Node** in Krill UI
2. **Configure:**
   - Filename: `my_script.py` (just filename, not full path)
   - Source: DataPoint that triggers execution
   - Target: DataSource to store results
3. **Test:** Update source and verify target receives output

## Troubleshooting

### Script Not Executing
```bash
# Check Krill logs
sudo journalctl -u krill -f | grep "Executing Python script"
```

### Permission Denied
```bash
# Fix ownership and permissions
sudo chown krill:krill /opt/krill/lambdas/my_script.py
sudo chmod 755 /opt/krill/lambdas/my_script.py
```

### Script Timeout
Scripts must complete within 30 seconds. If your script times out:
- Optimize the algorithm
- Process less data at once
- Consider breaking into multiple steps

### Import Errors
The sandbox environment has limited Python packages. If you need additional packages:

1. Install system-wide (not recommended for security):
   ```bash
   sudo apt-get install python3-<package>
   ```

2. Better: Keep scripts simple and use standard library only

## External Dependencies

If your script absolutely requires external Python packages:

1. Install them system-wide:
   ```bash
   sudo apt-get update
   sudo apt-get install python3-requests python3-numpy
   ```

2. Test that they work in sandbox:
   ```bash
   sudo -u krill firejail \
     --noprofile \
     --whitelist=/opt/krill/lambdas \
     python3 -c "import requests; print('OK')"
   ```

**Note:** Be cautious with external dependencies. They:
- Increase attack surface
- May not work in sandboxed environment
- Can have security vulnerabilities
- Make updates more complex

## Security Considerations

### User Responsibility
While Krill provides sandboxing, you are responsible for:
- ✅ Only adding scripts you trust and understand
- ✅ Reviewing any external dependencies
- ✅ Testing scripts before deployment
- ✅ Monitoring script execution in logs
- ✅ Keeping your system updated

### Path Security
- Scripts must be in `/opt/krill/lambdas/`
- Krill validates paths to prevent traversal attacks
- Symlinks are resolved and validated

### Network Access
By default, scripts CAN make network requests. This is intentional for IoT/API integration use cases.

If you want to block network access, you can configure the sandbox (requires code modification or future configuration support).

## Need Help?

- **Documentation:** See [Lambda Python Executor Security](/posts/lambda-python-executor-security/)
- **Examples:** Review the included example scripts in this directory
- **Support:** Open an issue on GitHub
- **Logs:** `sudo journalctl -u krill -f`

## Advanced: Manual Sandbox Configuration

The sandbox configuration is set in code with these defaults:
- Memory limit: 256MB
- Network: Allowed
- Timeout: 30 seconds
- Path: `/opt/krill/lambdas`

Future versions will support runtime configuration.

---

**Directory Ownership:**
```
Owner: krill:krill
Permissions: 0750 (rwxr-x---)
```

**Script Permissions:**
```
Owner: krill:krill
Permissions: 0755 (rwxr-xr-x)
```

**System User:**
- User: `krill` (system user, no login)
- Groups: `krill` + hardware groups (gpio, i2c, spi, etc.)
- Shell: `/usr/sbin/nologin`

