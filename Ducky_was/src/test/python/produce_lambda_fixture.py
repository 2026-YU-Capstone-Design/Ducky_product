"""Synthetic cross-language proof; imports read-only owner snapshot, never AWS clients."""
import base64
import hashlib
import io
import json
import os
import pathlib
import sys
sys.path.insert(0,"/owner/Ducky_rag")
from botocore.exceptions import ClientError
from ducky_rag.lambda_handler import DocumentWorker, Settings
from ducky_rag.aws_contract import json_bytes

request=json.loads(base64.b64decode(os.environ["DOCUMENT_FIXTURE_INPUT"]))
if request["action"] == "source":
    if request["type"] == "pdf":
        import pymupdf
        doc=pymupdf.open()
        for text in ["Quasar retry isolation policy. First page source accountability.", "Second page: independent document provenance and ownership."]:
            page=doc.new_page()
            page.insert_text((72,72),text)
        data=doc.tobytes()
    else:
        data=("Quasar retry isolation policy. "*200).encode()
    print(json.dumps({"source":base64.b64encode(data).decode()}))
    sys.exit(0)

source=base64.b64decode(request["sourceBytes"])
class S3:
    def __init__(self): self.objects={}; self.writes=0
    def get_object(self, **kw):
        if kw["Key"] == request["source"]["key"]:
            assert kw["VersionId"]==request["source"]["versionId"]
            assert kw["IfMatch"]=='"'+request["source"]["eTag"]+'"'
            return {"Body":io.BytesIO(source),"ContentLength":len(source),"VersionId":kw["VersionId"],"ETag":kw["IfMatch"],"Metadata":{"source-sha256":hashlib.sha256(source).hexdigest()}}
        if kw["Key"] not in self.objects:
            raise ClientError({"Error":{"Code":"NoSuchKey"}},"GetObject")
        return {"Body":io.BytesIO(self.objects[kw["Key"]])}
    def put_object(self, **kw):
        assert kw["Bucket"]==request["source"]["bucket"]
        assert kw["IfNoneMatch"]=="*"
        assert kw["Key"] not in self.objects
        self.objects[kw["Key"]]=kw["Body"]
        self.writes+=1
class SQS:
    def __init__(self):self.bodies=[]
    def send_message(self, **kw):self.bodies.append(kw["MessageBody"])
s3=S3();sqs=SQS()
settings=Settings(request["source"]["bucket"],"arn:aws:sqs:ap-southeast-2:123456789012:request","https://sqs.ap-southeast-2.amazonaws.com/123456789012/completion",request["revision"],"ap-southeast-2")
notification={"Records":[{"eventVersion":"2.1","eventSource":"aws:s3","awsRegion":settings.region,"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":settings.bucket},"object":{"key":request["source"]["key"],"versionId":request["source"]["versionId"],"eTag":request["source"]["eTag"],"size":len(source),"sequencer":"ABC123"}}}]}
event={"Records":[{"messageId":"synthetic-message","eventSource":"aws:sqs","eventSourceARN":settings.request_queue_arn,"body":json.dumps(notification)}]}
worker=DocumentWorker(settings,s3,sqs)
assert worker.handle(event)=={"batchItemFailures":[]}
initial_writes=s3.writes
assert worker.handle(event)=={"batchItemFailures":[]}
assert s3.writes==initial_writes and len(sqs.bodies)==2 and sqs.bodies[0]==sqs.bodies[1]
root=pathlib.Path("/owner/Ducky_rag/ducky_rag")
import importlib
module_files={name:str(pathlib.Path(importlib.import_module("ducky_rag."+name[:-3]).__file__).resolve()) for name in ["aws_contract.py","lambda_handler.py","pipeline.py","normalization.py","chunking.py"]}
assert all(pathlib.Path(value).is_relative_to(root) for value in module_files.values())
hashes={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in ["aws_contract.py","lambda_handler.py","pipeline.py","normalization.py","chunking.py"]}
print(json.dumps({"completion":sqs.bodies[0],"objects":{key:base64.b64encode(value).decode() for key,value in s3.objects.items()},"python":sys.version,"moduleFiles":module_files,"sourceHashes":hashes,"writes":initial_writes,"duplicateReused":True}))
