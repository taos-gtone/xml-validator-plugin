# -*- coding: utf-8 -*-
import os
import codecs

# 샘플 XML 파일 생성 (EUC-KR 인코딩)
# 오직 YAML 규칙 파일만 참고하여 정합성을 만족하는 샘플 생성

examples_dir = os.path.dirname(os.path.abspath(__file__)) + "/examples"
os.makedirs(examples_dir, exist_ok=True)

# YAML 규칙 기반 정합성 조건:
# - STR: Version="5.0", Code="BA"(일반금융기관) 또는 "CA"(카지노)
# - Organization: OrgName(Code), MainAuthor(Userid), Manager, Phone, Address(ZipCode), Email(0..1)
#   * Organization/Address에는 ZipCode만 있고 Code는 없음
# - Master: FiuDocNum(17자리), FormerFiuDocNum(0..1), StartDate, EndDate, InnerCount, OuterCount,
#           InnerKRWAmount, OuterKRWAmount, InnerUSDAmount, OuterUSDAmount, Count, KRWAmount, USDAmount,
#           MessageTypeCode(01,04,96,99), DocSendDate, Suspicion(0..1), SuspicionReport
# - Suspicion: QuestionTitle(Code: 100,200,300,400) > Question(Code: 3자리)
# - SuspicionReport: Who(0..1), When(0..1), Where(0..1), What(0..1), How(0..1), Why(필수),
#                    SyntheticOpinion(필수), BranchOfficeScore(1-5), OrgScore(1-5),
#                    RelationFiuDocNum(0..1), EtcPecularityType(0..1)
# - validation_rule: Suspicion이 있으면 EtcPecularityType은 비어있어야 함
# - Detail > Transaction: Seq, Date, Time, Channel(Code), Mean(Code), Method(Code), Goods(Code),
#                         MoneyType(Code), KRWAmount, ForeignAmount, USDAmount, OrgName(Code),
#                         BranchOffice(ZipCode, Code)(0..1), UserRelation(1..n), AccountRelation(0..n)
# - UserRelation: RelationRole(Code), RealNumber(Code), InsuRelDesc(0..1)
# - AccountRelation: OrgName(Code), AccountNumber, AccountRole(Code)
# - User: RealNumber(Code), RealNumberTypeName(0..1), Name, Nationality(Code)(0..1), Phone(HandPhone)(0..1),
#         Address(ZipCode)(0..1), BirthDay(0..1), Gender(Code)(0..1), OccupationType(Code)(0..1)
#   * RealNumber Code와 RealNumberTypeName은 코드/코드값의 관계:
#     01: 주민등록번호(개인), 02: 주민등록번호(기타단체), 03: 사업자등록번호, 04: 여권번호,
#     05: 법인등록번호, 06: 외국인등록번호, 07: 국내거소신고번호, 08: 투자등록번호/LEI,
#     09: 고유번호/납세번호, 11: BIC코드(SWIFT), 12: 해당국가법인번호, 14: CI번호, 99: 기타
# - Account(0..n): OrgName(Code), BranchOffice(ZipCode, Code)(0..1), AccountNumber, RegDate(0..1),
#                  AccountUser(Code), AgentFlag

# 개인 User 샘플 (Suspicion 있음)
sample_valid = '''<?xml version="1.0" encoding="EUC-KR"?>
<str:STR xmlns:str="http://www.kofiu.go.kr/str" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.kofiu.go.kr/str" Version="5.0" Code="BA">
	<Organization>
		<OrgName Code="AB0001">샘플금융기관</OrgName>
		<MainAuthor Userid="sample01">보고담당자</MainAuthor>
		<Manager>보고책임자</Manager>
		<Phone>02-1234-5678</Phone>
		<Address ZipCode="12345">서울특별시 중구 샘플로 100</Address>
		<Email>sample@sample.co.kr</Email>
	</Organization>
	<Master>
		<FiuDocNum>AB000120240100001</FiuDocNum>
		<FormerFiuDocNum/>
		<StartDate>20240101</StartDate>
		<EndDate>20240131</EndDate>
		<InnerCount>1</InnerCount>
		<OuterCount>0</OuterCount>
		<InnerKRWAmount>1000000</InnerKRWAmount>
		<OuterKRWAmount>0</OuterKRWAmount>
		<InnerUSDAmount>0</InnerUSDAmount>
		<OuterUSDAmount>0</OuterUSDAmount>
		<Count>1</Count>
		<KRWAmount>1000000</KRWAmount>
		<USDAmount>0</USDAmount>
		<MessageTypeCode>01</MessageTypeCode>
		<DocSendDate>20240201</DocSendDate>
		<Suspicion>
			<QuestionTitle Code="100">
				<Question Code="101">실명노출 기피 또는 거래에 대한 비밀요구</Question>
			</QuestionTitle>
			<QuestionTitle Code="200">
				<Question Code="207">거액 입금 후 당일 또는 익일 중 인출</Question>
			</QuestionTitle>
		</Suspicion>
		<SuspicionReport>
			<Who>의심거래자 정보</Who>
			<When>2024년 1월 15일</When>
			<Where>샘플금융기관 본점</Where>
			<What>현금 입금</What>
			<How>창구 거래</How>
			<Why>거래 패턴이 일반적이지 않아 의심됨</Why>
			<SyntheticOpinion>고객의 거래 패턴이 일반적인 금융거래 양식과 상이하여 의심거래로 판단됨</SyntheticOpinion>
			<BranchOfficeScore>3</BranchOfficeScore>
			<OrgScore>3</OrgScore>
			<RelationFiuDocNum/>
			<EtcPecularityType/>
		</SuspicionReport>
	</Master>
	<Detail>
		<Transaction>
			<Seq>1</Seq>
			<Date>20240115</Date>
			<Time>143000</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="01">입금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>1000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="01">9001011234567</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>1234567890123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<User>
			<RealNumber Code="01">9001011234567</RealNumber>
			<RealNumberTypeName>주민등록번호(개인)</RealNumberTypeName>
			<Name>홍길동</Name>
			<Nationality Code="KR">대한민국</Nationality>
			<Phone HandPhone="010-1234-5678">02-1234-5678</Phone>
			<Address ZipCode="12345">서울특별시 강남구 샘플로 200</Address>
			<BirthDay>19900101</BirthDay>
			<Gender Code="1">남</Gender>
			<OccupationType Code="01">직장인</OccupationType>
		</User>
		<Account>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<AccountNumber>1234567890123</AccountNumber>
			<RegDate>20230101</RegDate>
			<AccountUser Code="01">9001011234567</AccountUser>
			<AgentFlag>N</AgentFlag>
		</Account>
	</Detail>
	<FileAttach/>
</str:STR>
'''

# 법인 User 샘플 (Suspicion 있음)
sample_corp = '''<?xml version="1.0" encoding="EUC-KR"?>
<str:STR xmlns:str="http://www.kofiu.go.kr/str" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.kofiu.go.kr/str" Version="5.0" Code="BA">
	<Organization>
		<OrgName Code="AB0001">샘플금융기관</OrgName>
		<MainAuthor Userid="sample01">보고담당자</MainAuthor>
		<Manager>보고책임자</Manager>
		<Phone>02-1234-5678</Phone>
		<Address ZipCode="12345">서울특별시 중구 샘플로 100</Address>
		<Email>sample@sample.co.kr</Email>
	</Organization>
	<Master>
		<FiuDocNum>AB000120240100002</FiuDocNum>
		<FormerFiuDocNum/>
		<StartDate>20240101</StartDate>
		<EndDate>20240131</EndDate>
		<InnerCount>1</InnerCount>
		<OuterCount>0</OuterCount>
		<InnerKRWAmount>50000000</InnerKRWAmount>
		<OuterKRWAmount>0</OuterKRWAmount>
		<InnerUSDAmount>0</InnerUSDAmount>
		<OuterUSDAmount>0</OuterUSDAmount>
		<Count>1</Count>
		<KRWAmount>50000000</KRWAmount>
		<USDAmount>0</USDAmount>
		<MessageTypeCode>01</MessageTypeCode>
		<DocSendDate>20240201</DocSendDate>
		<Suspicion>
			<QuestionTitle Code="100">
				<Question Code="103">업력이나 업체규모, 개인능력에 비해 과다한 거래실적</Question>
			</QuestionTitle>
			<QuestionTitle Code="200">
				<Question Code="201">갑작스러운 거래패턴의 변화</Question>
			</QuestionTitle>
		</Suspicion>
		<SuspicionReport>
			<Who>법인 의심거래자 정보</Who>
			<When>2024년 1월 20일</When>
			<Where>샘플금융기관 본점</Where>
			<What>법인 계좌 입금</What>
			<How>창구 거래</How>
			<Why>법인 규모 대비 과다한 거래금액으로 의심됨</Why>
			<SyntheticOpinion>법인의 업력 및 규모 대비 과다한 거래금액이 발생하여 의심거래로 판단됨</SyntheticOpinion>
			<BranchOfficeScore>4</BranchOfficeScore>
			<OrgScore>4</OrgScore>
			<RelationFiuDocNum/>
			<EtcPecularityType/>
		</SuspicionReport>
	</Master>
	<Detail>
		<Transaction>
			<Seq>1</Seq>
			<Date>20240120</Date>
			<Time>103000</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="01">입금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>50000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<User>
			<RealNumber Code="03">1234567890</RealNumber>
			<RealNumberTypeName>사업자등록번호</RealNumberTypeName>
			<Name>샘플주식회사</Name>
			<Nationality Code="KR">대한민국</Nationality>
			<Phone HandPhone="">02-9876-5432</Phone>
			<Address ZipCode="06234">서울특별시 강남구 테헤란로 123</Address>
			<BirthDay>20100315</BirthDay>
			<CeoName>김대표</CeoName>
			<KSIC Code="46499">기타 상품 도매업</KSIC>
			<BizAddress ZipCode="06234">서울특별시 강남구 테헤란로 123</BizAddress>
			<BizTelNo>02-9876-5432</BizTelNo>
			<HomepageURL>http://www.samplecorp.co.kr</HomepageURL>
			<BizScale Code="02">중소기업</BizScale>
			<IsBankingOrgan>N</IsBankingOrgan>
			<IsNonProfitCorp>N</IsNonProfitCorp>
			<IsNationalPublicGroup>N</IsNationalPublicGroup>
			<IsStockList>N</IsStockList>
		</User>
		<Account>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<AccountNumber>9876543210123</AccountNumber>
			<RegDate>20200601</RegDate>
			<AccountUser Code="03">1234567890</AccountUser>
			<AgentFlag>N</AgentFlag>
		</Account>
	</Detail>
	<FileAttach/>
</str:STR>
'''

# 법인 User + 여러 Transaction 샘플
sample_corp_multi = '''<?xml version="1.0" encoding="EUC-KR"?>
<str:STR xmlns:str="http://www.kofiu.go.kr/str" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.kofiu.go.kr/str" Version="5.0" Code="BA">
	<Organization>
		<OrgName Code="AB0001">샘플금융기관</OrgName>
		<MainAuthor Userid="sample01">보고담당자</MainAuthor>
		<Manager>보고책임자</Manager>
		<Phone>02-1234-5678</Phone>
		<Address ZipCode="12345">서울특별시 중구 샘플로 100</Address>
		<Email>sample@sample.co.kr</Email>
	</Organization>
	<Master>
		<FiuDocNum>AB000120240100003</FiuDocNum>
		<FormerFiuDocNum/>
		<StartDate>20240101</StartDate>
		<EndDate>20240131</EndDate>
		<InnerCount>3</InnerCount>
		<OuterCount>2</OuterCount>
		<InnerKRWAmount>150000000</InnerKRWAmount>
		<OuterKRWAmount>80000000</OuterKRWAmount>
		<InnerUSDAmount>0</InnerUSDAmount>
		<OuterUSDAmount>0</OuterUSDAmount>
		<Count>5</Count>
		<KRWAmount>230000000</KRWAmount>
		<USDAmount>0</USDAmount>
		<MessageTypeCode>01</MessageTypeCode>
		<DocSendDate>20240201</DocSendDate>
		<Suspicion>
			<QuestionTitle Code="100">
				<Question Code="103">업력이나 업체규모, 개인능력에 비해 과다한 거래실적</Question>
			</QuestionTitle>
			<QuestionTitle Code="200">
				<Question Code="207">거액 입금 후 당일 또는 익일 중 인출</Question>
				<Question Code="215">빈번한 입출금(입출고)</Question>
			</QuestionTitle>
		</Suspicion>
		<SuspicionReport>
			<Who>법인 의심거래자 정보</Who>
			<When>2024년 1월</When>
			<Where>샘플금융기관 본점 및 강남지점</Where>
			<What>법인 계좌 다수 입출금</What>
			<How>창구 및 인터넷뱅킹 거래</How>
			<Why>법인 규모 대비 과다한 거래금액 및 빈번한 입출금으로 의심됨</Why>
			<SyntheticOpinion>법인의 업력 및 규모 대비 과다한 거래금액이 다수 발생하고 입금 후 단기간 내 출금이 반복되어 의심거래로 판단됨</SyntheticOpinion>
			<BranchOfficeScore>4</BranchOfficeScore>
			<OrgScore>5</OrgScore>
			<RelationFiuDocNum/>
			<EtcPecularityType/>
		</SuspicionReport>
	</Master>
	<Detail>
		<Transaction>
			<Seq>1</Seq>
			<Date>20240105</Date>
			<Time>093000</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="01">입금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>50000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<Transaction>
			<Seq>2</Seq>
			<Date>20240105</Date>
			<Time>153000</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="02">출금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>30000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<Transaction>
			<Seq>3</Seq>
			<Date>20240115</Date>
			<Time>101500</Time>
			<Channel Code="04">인터넷뱅킹</Channel>
			<Mean Code="06">대체</Mean>
			<Method Code="01">입금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>70000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<Transaction>
			<Seq>4</Seq>
			<Date>20240116</Date>
			<Time>140000</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="02">출금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>50000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="06234" Code="0000002">강남지점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<Transaction>
			<Seq>5</Seq>
			<Date>20240125</Date>
			<Time>111500</Time>
			<Channel Code="01">창구</Channel>
			<Mean Code="01">현금</Mean>
			<Method Code="01">입금</Method>
			<Goods Code="01">수시입출금 예금</Goods>
			<MoneyType Code="KRW">한국 원</MoneyType>
			<KRWAmount>30000000</KRWAmount>
			<ForeignAmount>0</ForeignAmount>
			<USDAmount>0</USDAmount>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<UserRelation>
				<RelationRole Code="01">의심거래자</RelationRole>
				<RealNumber Code="03">1234567890</RealNumber>
				<InsuRelDesc/>
			</UserRelation>
			<AccountRelation>
				<OrgName Code="AB0001">샘플금융기관</OrgName>
				<AccountNumber>9876543210123</AccountNumber>
				<AccountRole Code="01">관련계좌</AccountRole>
			</AccountRelation>
		</Transaction>
		<User>
			<RealNumber Code="03">1234567890</RealNumber>
			<RealNumberTypeName>사업자등록번호</RealNumberTypeName>
			<Name>샘플주식회사</Name>
			<Nationality Code="KR">대한민국</Nationality>
			<Phone HandPhone="">02-9876-5432</Phone>
			<Address ZipCode="06234">서울특별시 강남구 테헤란로 123</Address>
			<BirthDay>20100315</BirthDay>
			<CeoName>김대표</CeoName>
			<KSIC Code="46499">기타 상품 도매업</KSIC>
			<BizAddress ZipCode="06234">서울특별시 강남구 테헤란로 123</BizAddress>
			<BizTelNo>02-9876-5432</BizTelNo>
			<HomepageURL>http://www.samplecorp.co.kr</HomepageURL>
			<BizScale Code="02">중소기업</BizScale>
			<IsBankingOrgan>N</IsBankingOrgan>
			<IsNonProfitCorp>N</IsNonProfitCorp>
			<IsNationalPublicGroup>N</IsNationalPublicGroup>
			<IsStockList>N</IsStockList>
		</User>
		<Account>
			<OrgName Code="AB0001">샘플금융기관</OrgName>
			<BranchOffice ZipCode="12345" Code="0000001">본점</BranchOffice>
			<AccountNumber>9876543210123</AccountNumber>
			<RegDate>20200601</RegDate>
			<AccountUser Code="03">1234567890</AccountUser>
			<AgentFlag>N</AgentFlag>
		</Account>
	</Detail>
	<FileAttach/>
</str:STR>
'''

# 파일 저장
samples = [
    ('sample_str_valid.xml', sample_valid),
    ('sample_str_corp.xml', sample_corp),
    ('sample_str_corp_multi_tx.xml', sample_corp_multi),
]

for filename, content in samples:
    filepath = os.path.join(examples_dir, filename)
    with codecs.open(filepath, 'w', encoding='euc-kr') as f:
        f.write(content)
    print(f"Created: {filepath}")
