import sys, os, subprocess, lorun, shlex
import config as cfg
import json, string
import signal  
import time  

class TimeOutException(Exception):  
    pass

def clearTempDir(path):
    paths = os.listdir(path)
    for file_name in paths:
        os.remove(os.path.join(path, file_name))


def compile(language):
    def handle(signum, frame):  
        raise TimeOutException("compile timeOut")
    language = language.lower()
    build_cmd = {
        "gcc": "gcc %s -o %s " % (os.path.join(cfg.source_path, cfg.gcc_file_name), cfg.exec_name),
        "g++": "g++ %s -o %s " % (os.path.join(cfg.source_path, cfg.gpp_file_name), cfg.exec_name),
        "java": "javac %s -d %s " % (os.path.join(cfg.source_path, cfg.java_file_name), cfg.tempdata_path),
        "python": "python -m py_compile %s " % (os.path.join(cfg.source_path, cfg.python_file_name))
    }
    if language not in build_cmd.keys():
        return False
    fdout = open("templog.out", 'w')
    fderr = open("templog.err", 'w')
    p = subprocess.Popen(build_cmd[language],shell=True,stdout=fdout,stderr=fderr)
    try:
        signal.signal(signal.SIGALRM, handle)
        signal.alarm(1)
        out, err = p.communicate()
        signal.alarm(0)
        if p.returncode == 0:
            return True, None
        return False, get_text_file("templog.err")
    except TimeOutException, e:  
        return False, "compile timeOut"
    except Exception, e:
        return False, "compile error"

def get_text_file(filename):
    if not os.path.exists(filename):
        print("ERROR: file not exit: %s" % (filename))
        return None
    if not os.path.isfile(filename):
        print("ERROR: %s not a filename." % (filename))
        return None
    f = open(filename, "r")
    content = f.read()
    f.close()
    return content
        
def run(language, testdata_path, standout_path, limitTime, limitMemory):
    language = language.lower()
    cmd = ''
    if language == 'java':
        cmd = 'java -classpath %s %s ' % (cfg.tempdata_path, cfg.java_file_name_no_ext)
    elif language == 'python':
        cmd = 'python %s ' % (os.path.join(cfg.source_path, cfg.python_file_name_no_ext + ".pyc"))
    else:
        cmd = './%s ' % (cfg.exec_name)
    fin = open(testdata_path)
    ftemp = open(cfg.temp_file_name, 'w')
    ftemp = open(os.path.join(cfg.tempdata_path, cfg.temp_file_name), 'w')
    runcfg = {
        'args': shlex.split(cmd),
        'fd_in': fin.fileno(),
        'fd_out': ftemp.fileno(),
        'timelimit': int(limitTime),
        'memorylimit': int(limitMemory),
    }
    res = lorun.run(runcfg)
    fin.close()
    ftemp.close()
    if res['result'] == 0:
        ftemp = open(os.path.join(cfg.tempdata_path, cfg.temp_file_name))
        fout = open(standout_path)
        res['result'] = lorun.check(fout.fileno(), ftemp.fileno())
        fout.close()
        ftemp.close()
    return res


def getResult(testdata_id, res, errMsg, useTime, useMemory):
    return {
        'testId': testdata_id,
        'status': res,
        'errMsg': errMsg,
        'usedTime': useTime,
        'usedMemory': useMemory,
    }


def judge(pid, testDataNum, language, limitTime, limitMemory, testModel):
    compileRes, compileResMsg = compile(language)
    ans = []
    if compileRes == True:
        if testModel == '1':
            testDataPath = os.path.join(cfg.special_testdata_path, pid)
        else:
            testDataPath = os.path.join(cfg.standard_testdata_path, pid)
        for i in range(0, string.atoi(testDataNum, 10), 1):
            if os.path.exists(testDataPath + '/' + str(i) + '.in') != True:
                ans.append(getResult(i, 10, 'Missing input file', -1, -1))
            elif os.path.exists(testDataPath + '/' + str(i) + '.out') != True:
                ans.append(getResult(i, 10, 'Missing output file', -1, -1))
            else:
                stdin_path = os.path.join(testDataPath, str(i) + '.in')
                stdout_path = os.path.join(testDataPath, str(i) + '.out')
                res = run(language, stdin_path, stdout_path, limitTime, limitMemory)
                ans.append(getResult(i, res['result'] + 1, '', res['timeused'], res['memoryused']))
    else:
        ans.append(getResult(-1, 8, compileResMsg, -1, -1))
    return ans


if __name__ == '__main__':
    if len(sys.argv) != 7:
        exit(-1)
    else:
        try:
            pid = sys.argv[1]
            testDataDum = sys.argv[2]
            language = sys.argv[3]
            limitTime = sys.argv[4]
            limitMemory = sys.argv[5]
            testModel = sys.argv[6]
            data = judge(pid, testDataDum, language, limitTime, limitMemory, testModel)
            jsonStr = json.dumps(data)
            print jsonStr
        except Exception, e:
            print e
    	    pass
        finally:
            clearTempDir(cfg.tempdata_path)
